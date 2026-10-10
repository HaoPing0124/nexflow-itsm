package com.nexflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexflow.config.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NEXFLOW 后端核心场景集成测试。
 *
 * 覆盖：认证、RBAC、工单 CRUD、自定义字段必填校验、状态机全流程与非法流转拒绝、
 * 统计一致性、以及响应包封一致性（配置中心懒加载 500 的回归防线）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NexflowApiIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PWD = "admin123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private DataSeeder dataSeeder;

    @BeforeEach
    void ensureSeeded() {
        // 幂等：已有用户时直接返回。放在这里是为了不依赖 ApplicationReadyEvent 的顺序。
        dataSeeder.seed();
    }

    // ---------------- helpers ----------------

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String username, String password) throws Exception {
        ObjectNode body = om.createObjectNode();
        body.put("username", username);
        body.put("password", password);
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resp = json(r);
        assertThat(resp.get("code").asInt()).isZero();
        return resp.get("data").get("token").asText();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult r = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json(r);
    }

    /** 取一个"无需审批"的工单类型（自动分配可跑通），并按其必填字段构造合法自定义字段值。 */
    private JsonNode firstNonApprovalType(String token) throws Exception {
        ArrayNode types = (ArrayNode) getJson("/api/config/ticket-types", token).get("data");
        for (JsonNode t : types) {
            if (!t.get("requiresApproval").asBoolean()) {
                return t;
            }
        }
        throw new IllegalStateException("种子数据中没有无需审批的工单类型");
    }

    private ArrayNode requiredCustomFieldValues(long typeId, String token) throws Exception {
        ArrayNode defs = (ArrayNode) getJson("/api/config/ticket-types/" + typeId + "/fields", token).get("data");
        ArrayNode values = om.createArrayNode();
        for (JsonNode d : defs) {
            if (!d.get("required").asBoolean()) {
                continue;
            }
            String fieldType = d.get("fieldType").asText();
            String value = switch (fieldType) {
                case "DATE" -> "2026-10-10";
                case "BOOLEAN" -> "true";
                case "NUMBER" -> "1";
                default -> "集成测试";
            };
            JsonNode options = d.get("optionsJson");
            if ("SELECT".equals(fieldType) && options != null && options.isTextual() && !options.asText().isBlank()) {
                JsonNode parsed = om.readTree(options.asText());
                if (parsed.isArray() && !parsed.isEmpty()) {
                    value = parsed.get(0).path("value").asText(value);
                }
            }
            ObjectNode v = om.createObjectNode();
            v.put("fieldId", d.get("id").asLong());
            v.put("value", value);
            values.add(v);
        }
        return values;
    }

    private ObjectNode createTicketPayload(long typeId, String title, ArrayNode customFields) {
        ObjectNode payload = om.createObjectNode();
        payload.put("typeId", typeId);
        payload.put("title", title);
        payload.put("description", "集成测试创建");
        payload.put("priority", "HIGH");
        payload.set("customFields", customFields);
        payload.set("attachmentIds", om.createArrayNode());
        return payload;
    }

    private JsonNode createTicket(String token, long typeId, String title) throws Exception {
        ObjectNode payload = createTicketPayload(typeId, title, requiredCustomFieldValues(typeId, token));
        MvcResult r = mvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resp = json(r);
        assertThat(resp.get("code").asInt())
                .as("创建工单应成功，实际响应: %s", resp.toString())
                .isZero();
        return resp.get("data");
    }

    private JsonNode act(String token, long ticketId, String action, String bodyJson) throws Exception {
        var req = post("/api/tickets/" + ticketId + "/" + action)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
        if (bodyJson != null) {
            req = req.content(bodyJson);
        }
        MvcResult r = mvc.perform(req).andExpect(status().isOk()).andReturn();
        return json(r);
    }

    // ---------------- tests ----------------

    @Test
    @DisplayName("登录返回 JWT 与管理员角色/权限")
    void login_returnsTokenAndRoles() throws Exception {
        ObjectNode body = om.createObjectNode();
        body.put("username", ADMIN);
        body.put("password", ADMIN_PWD);
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json(r).get("data");
        assertThat(data.get("token").asText()).isNotBlank();
        assertThat(data.get("user").get("roles").toString()).contains("ADMIN");
        assertThat(data.get("user").get("permissions").size()).isGreaterThan(5);
    }

    @Test
    @DisplayName("未携带 token 访问受保护接口返回 401")
    void unauthenticated_isRejected() throws Exception {
        mvc.perform(get("/api/stats/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("配置中心接口返回统一包封（懒加载 500 的回归防线）")
    void configEndpoints_returnWrappedPayload() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        for (String url : new String[]{"/api/config/users?size=5", "/api/config/roles", "/api/config/depts"}) {
            JsonNode resp = getJson(url, token);
            assertThat(resp.get("code").asInt()).as(url + " 应返回 code=0").isZero();
            assertThat(resp.get("data").isNull()).as(url + " 的 data 不应为空").isFalse();
        }
    }

    @Test
    @DisplayName("通知列表返回统一包封与分页结构")
    void notifications_returnWrappedPage() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode resp = getJson("/api/notifications?unread=true", token);
        assertThat(resp.get("code").asInt()).isZero();
        assertThat(resp.get("data").has("items")).isTrue();
        assertThat(resp.get("data").has("total")).isTrue();
    }

    @Test
    @DisplayName("普通员工无权访问配置中心（RBAC 服务端强制）")
    void employee_cannotAccessConfig() throws Exception {
        String token = login("linxiao", "lx123");
        mvc.perform(get("/api/config/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("缺失必填自定义字段时创建工单被拒绝")
    void createTicket_missingRequiredField_isRejected() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode type = firstNonApprovalType(token);
        ObjectNode payload = createTicketPayload(type.get("id").asLong(), "缺少必填字段的工单", om.createArrayNode());
        MvcResult r = mvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(payload)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isBetween(400, 499);
    }

    @Test
    @DisplayName("工单可创建并持久化自定义字段值")
    void createTicket_persistsCustomFields() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode type = firstNonApprovalType(token);
        JsonNode created = createTicket(token, type.get("id").asLong(), "自定义字段持久化校验");

        assertThat(created.get("ticketNo").asText()).startsWith("NF-");
        assertThat(created.get("status").asText()).isEqualTo("CREATED");
        assertThat(created.get("customFields").size())
                .as("必填自定义字段应被持久化")
                .isEqualTo(requiredCustomFieldValues(type.get("id").asLong(), token).size());

        // 重新读取，验证确实落库（而非仅回显）
        JsonNode reread = getJson("/api/tickets/" + created.get("id").asLong(), token).get("data");
        assertThat(reread.get("customFields").size()).isEqualTo(created.get("customFields").size());
    }

    @Test
    @DisplayName("状态机全流程：创建→分类→自动分配→处理→解决→关闭")
    void ticketLifecycle_fromCreatedToClosed() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode type = firstNonApprovalType(token);
        long id = createTicket(token, type.get("id").asLong(), "全流程生命周期校验").get("id").asLong();

        String status = "CREATED";
        String[] steps = {"classify", "auto_assign", "start", "resolve", "confirm"};
        String[] expected = {"CLASSIFIED", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"};
        for (int i = 0; i < steps.length; i++) {
            JsonNode resp = act(token, id, steps[i], null);
            assertThat(resp.get("code").asInt())
                    .as("步骤 %s 应成功，响应: %s", steps[i], resp.toString())
                    .isZero();
            status = resp.get("data").get("status").asText();
            assertThat(status).as("执行 %s 后状态", steps[i]).isEqualTo(expected[i]);
        }

        JsonNode detail = getJson("/api/tickets/" + id, token).get("data");
        assertThat(detail.get("status").asText()).isEqualTo("CLOSED");
        assertThat(detail.get("assigneeId").isNull()).as("自动分配后应有处理人").isFalse();
        assertThat(detail.get("assigneeName").asText()).as("处理人姓名应可读").isNotBlank();
        assertThat(detail.get("statusHistory").size())
                .as("每次流转都应留下状态历史")
                .isGreaterThanOrEqualTo(5);
        assertThat(detail.get("assignmentTrace").size())
                .as("应留下可解释的分配轨迹")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("跨部门工单也能自动分配：非该部门员工提交的工单仍应命中处理团队规则")
    void autoAssign_matchesRuleAcrossDepartments() throws Exception {
        String admin = login(ADMIN, ADMIN_PWD);
        // linxiao 属于人事部；IT 支持规则的处理团队是 IT 部 —— 二者不同部门
        String employee = login("linxiao", "lx123");

        JsonNode type = firstNonApprovalType(admin);
        long typeId = type.get("id").asLong();

        // 找出该类型命中的规则，记录其处理团队部门
        ArrayNode rules = (ArrayNode) getJson("/api/assignment-rules", admin).get("data");
        Long ruleScopeDept = null;
        for (JsonNode r : rules) {
            if (r.get("active").asBoolean()
                    && r.get("ticketTypeId") != null && !r.get("ticketTypeId").isNull()
                    && r.get("ticketTypeId").asLong() == typeId) {
                ruleScopeDept = r.get("scopeDeptId").isNull() ? null : r.get("scopeDeptId").asLong();
                break;
            }
        }
        assertThat(ruleScopeDept).as("测试前提：该工单类型应存在带处理团队的启用规则").isNotNull();

        // 由人事部员工提交一张 IT 类工单
        ObjectNode payload = createTicketPayload(typeId, "跨部门 IT 工单", requiredCustomFieldValues(typeId, admin));
        JsonNode created = json(mvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("deptId").asLong())
                .as("测试前提：提交人部门应与规则的处理团队不同（即真正的跨部门场景）")
                .isNotEqualTo(ruleScopeDept);

        // 由管理员推进：分类 → 自动分配
        assertThat(act(admin, id, "classify", null).get("data").get("status").asText()).isEqualTo("CLASSIFIED");
        JsonNode after = act(admin, id, "auto_assign", null).get("data");

        assertThat(after.get("status").asText())
                .as("跨部门提交的工单也应被自动分配（scopeDeptId 是处理团队，不是工单部门的过滤条件）")
                .isEqualTo("ASSIGNED");
        assertThat(after.get("assigneeId").isNull()).as("应选出处理人").isFalse();
    }

    @Test
    @DisplayName("数据一致性：处于审批中的工单必须有待审批记录，且审批人能看到")
    void pendingApprovalTickets_havePendingApprovalRecords() throws Exception {
        String admin = login(ADMIN, ADMIN_PWD);
        ArrayNode items = (ArrayNode) getJson("/api/tickets?status=PENDING_APPROVAL&page=1&size=50", admin)
                .get("data").get("items");
        assertThat(items.size()).as("种子数据应包含处于审批中的工单").isGreaterThan(0);

        for (JsonNode t : items) {
            JsonNode approvals = getJson("/api/tickets/" + t.get("id").asLong(), admin).get("data").get("approvals");
            boolean hasPending = false;
            for (JsonNode a : approvals) {
                if ("PENDING".equals(a.get("status").asText())) {
                    hasPending = true;
                    break;
                }
            }
            assertThat(hasPending)
                    .as("工单 %s 处于 PENDING_APPROVAL，必须存在待审批记录，否则会卡死且审批中心为空",
                            t.get("ticketNo").asText())
                    .isTrue();
        }

        // 审批人的待审批列表不应为空
        String supervisor = login("supervisor", "sup123");
        long pending = getJson("/api/approvals?status=PENDING&page=1&size=20", supervisor)
                .get("data").get("total").asLong();
        assertThat(pending).as("主管的待审批列表应有数据").isGreaterThan(0);
    }

    @Test
    @DisplayName("审批决策对流程有约束作用：通过后工单必须离开待审批状态")
    void approvalDecision_advancesTicket() throws Exception {
        String supervisor = login("supervisor", "sup123");
        ArrayNode pendings = (ArrayNode) getJson("/api/approvals?status=PENDING&page=1&size=20", supervisor)
                .get("data").get("items");
        assertThat(pendings.size()).as("主管应有待审批事项").isGreaterThan(0);

        JsonNode target = pendings.get(0);
        long approvalId = target.get("id").asLong();
        long ticketId = target.get("ticketId").asLong();
        String ticketNo = target.get("ticketNo").asText();

        String admin = login(ADMIN, ADMIN_PWD);
        assertThat(getJson("/api/tickets/" + ticketId, admin).get("data").get("status").asText())
                .as("审批驱动前 %s 应处于待审批", ticketNo)
                .isEqualTo("PENDING_APPROVAL");

        ObjectNode body = om.createObjectNode();
        body.put("approve", true);
        body.put("comment", "集成测试：同意");
        MvcResult r = mvc.perform(post("/api/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(r).get("code").asInt()).isZero();

        String after = getJson("/api/tickets/" + ticketId, admin).get("data").get("status").asText();
        assertThat(after)
                .as("审批通过后 %s 必须离开 PENDING_APPROVAL", ticketNo)
                .isIn("CLASSIFIED", "ASSIGNED");
        assertThat(getJson("/api/approvals?status=PENDING&page=1&size=20", supervisor)
                .get("data").get("total").asLong())
                .as("已决策的审批不应再出现在待审批列表")
                .isLessThan(pendings.size());
    }

    @Test
    @DisplayName("批量流转允许部分成功：非法状态的项不拖垮整批")
    void batch_partialSuccessIsReported() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        ArrayNode createdItems = (ArrayNode) getJson("/api/tickets?status=CREATED&page=1&size=1", token)
                .get("data").get("items");
        ArrayNode closedItems = (ArrayNode) getJson("/api/tickets?status=CLOSED&page=1&size=1", token)
                .get("data").get("items");
        assertThat(createdItems.size()).isGreaterThan(0);
        assertThat(closedItems.size()).isGreaterThan(0);
        long okId = createdItems.get(0).get("id").asLong();
        long badId = closedItems.get(0).get("id").asLong();

        ObjectNode body = om.createObjectNode();
        body.put("action", "classify");
        ArrayNode ids = body.putArray("ids");
        ids.add(okId);
        ids.add(badId);

        // 关键：混合了不可流转的项时，也必须返回 200 而不是 500
        MvcResult r = mvc.perform(post("/api/tickets/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json(r).get("data");
        assertThat(data.get("success").size()).as("合法项应成功").isEqualTo(1);
        assertThat(data.get("success").get(0).asLong()).isEqualTo(okId);
        assertThat(data.get("failed").size()).as("非法项应被逐条回报失败原因").isEqualTo(1);
        assertThat(data.get("failed").get(0).get("id").asLong()).isEqualTo(badId);

        // 成功项确实已流转（证明部分成功真的落库，而非整体回滚）
        assertThat(getJson("/api/tickets/" + okId, token).get("data").get("status").asText())
                .as("成功项应已流转为 CLASSIFIED")
                .isEqualTo("CLASSIFIED");
    }

    @Test
    @DisplayName("非法流转被状态机拒绝")
    void illegalTransition_isRejected() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode type = firstNonApprovalType(token);
        long id = createTicket(token, type.get("id").asLong(), "非法流转校验").get("id").asLong();

        // CREATED 状态直接 start：跳过分类与分配，必须被拒绝
        MvcResult r = mvc.perform(post("/api/tickets/" + id + "/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("CREATED 直接开始处理应被拒绝")
                .isBetween(400, 499);

        JsonNode detail = getJson("/api/tickets/" + id, token).get("data");
        assertThat(detail.get("status").asText()).as("被拒绝后状态不应改变").isEqualTo("CREATED");
    }

    @Test
    @DisplayName("评论可写入并出现在详情中")
    void comment_isPersisted() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode type = firstNonApprovalType(token);
        long id = createTicket(token, type.get("id").asLong(), "评论持久化校验").get("id").asLong();

        ObjectNode body = om.createObjectNode();
        body.put("content", "集成测试评论");
        body.put("isInternal", false);
        MvcResult r = mvc.perform(post("/api/tickets/" + id + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(r).get("code").asInt()).isZero();

        JsonNode detail = getJson("/api/tickets/" + id, token).get("data");
        assertThat(detail.get("comments").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("统计口径与数据库一致：新建工单后总量增加")
    void statsOverview_reflectsNewTicket() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        long before = getJson("/api/stats/overview", token).get("data").get("total").asLong();

        JsonNode type = firstNonApprovalType(token);
        createTicket(token, type.get("id").asLong(), "统计一致性校验");

        long after = getJson("/api/stats/overview", token).get("data").get("total").asLong();
        assertThat(after).as("新建一张工单后总量应 +1").isEqualTo(before + 1);
    }

    @Test
    @DisplayName("工单列表分页结构正确且支持关键字过滤")
    void ticketList_isPagedAndFilterable() throws Exception {
        String token = login(ADMIN, ADMIN_PWD);
        JsonNode page = getJson("/api/tickets?page=1&size=5", token).get("data");
        assertThat(page.get("items").size()).isLessThanOrEqualTo(5);
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("size").asInt()).isEqualTo(5);
        assertThat(page.get("total").asLong()).isGreaterThan(0);
    }
}
