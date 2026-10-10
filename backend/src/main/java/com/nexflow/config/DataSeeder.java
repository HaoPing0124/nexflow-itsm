package com.nexflow.config;

import com.nexflow.approval.ApprovalRequest;
import com.nexflow.approval.ApprovalRequestRepository;
import com.nexflow.approval.ApprovalStatus;
import com.nexflow.assignment.AssignmentMode;
import com.nexflow.assignment.AssignmentRecord;
import com.nexflow.assignment.AssignmentRecordRepository;
import com.nexflow.assignment.AssignmentRule;
import com.nexflow.assignment.AssignmentRuleRepository;
import com.nexflow.assignment.AssignmentStrategy;
import com.nexflow.knowledge.FaqEntry;
import com.nexflow.knowledge.FaqEntryRepository;
import com.nexflow.sla.Holiday;
import com.nexflow.sla.HolidayRepository;
import com.nexflow.sla.SlaCalculator;
import com.nexflow.sla.SlaPolicy;
import com.nexflow.sla.SlaPolicyRepository;
import com.nexflow.ticket.Priority;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketAttachmentRepository;
import com.nexflow.ticket.TicketComment;
import com.nexflow.ticket.TicketCommentRepository;
import com.nexflow.ticket.TicketCustomFieldValue;
import com.nexflow.ticket.TicketCustomFieldValueRepository;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.ticket.TicketStatusHistory;
import com.nexflow.ticket.TicketStatusHistoryRepository;
import com.nexflow.tickettype.CustomFieldDef;
import com.nexflow.tickettype.CustomFieldDefRepository;
import com.nexflow.tickettype.FieldType;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.user.Dept;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.Permission;
import com.nexflow.user.PermissionRepository;
import com.nexflow.user.Role;
import com.nexflow.user.RoleRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserRepository;
import com.nexflow.user.UserStatus;
import com.nexflow.workflow.TicketState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    private final AppProperties props;
    private final DeptRepository deptRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CustomFieldDefRepository customFieldDefRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final HolidayRepository holidayRepository;
    private final AssignmentRuleRepository assignmentRuleRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final TicketRepository ticketRepository;
    private final TicketCustomFieldValueRepository customFieldValueRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final AssignmentRecordRepository assignmentRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!props.isSeedDemoData()) {
            return;
        }
        if (userRepository.count() > 0) {
            return;
        }
        log.info("NEXFLOW DataSeeder: 开始写入演示数据");
        seedPermissions();
        seedRoles();
        Map<String, Dept> depts = seedDepts();
        Map<String, User> users = seedUsers(depts);
        Map<String, SlaPolicy> policies = seedSlaPolicies();
        seedHolidays();
        Map<String, TicketType> types = seedTicketTypes(policies);
        seedRules(depts, types);
        seedFaqs();
        seedTickets(types, depts, users);
        log.info("NEXFLOW DataSeeder: 演示数据写入完成");
    }

    private void seedPermissions() {
        Map<String, String[]> perms = new HashMap<>();
        perms.put("ticket:create", new String[]{"创建工单", "ticket"});
        perms.put("ticket:view", new String[]{"查看工单", "ticket"});
        perms.put("ticket:classify", new String[]{"工单分类", "ticket"});
        perms.put("ticket:assign", new String[]{"工单分配", "ticket"});
        perms.put("ticket:resolve", new String[]{"工单处理", "ticket"});
        perms.put("ticket:close", new String[]{"确认关闭", "ticket"});
        perms.put("approval:decide", new String[]{"审批决策", "approval"});
        perms.put("config:write", new String[]{"配置管理", "config"});
        perms.put("sla:view", new String[]{"SLA 查看", "sla"});
        perms.put("audit:view", new String[]{"审计查看", "audit"});
        perms.put("stats:view", new String[]{"统计查看", "stats"});
        for (Map.Entry<String, String[]> entry : perms.entrySet()) {
            Permission permission = new Permission();
            permission.setCode(entry.getKey());
            permission.setName(entry.getValue()[0]);
            permission.setModule(entry.getValue()[1]);
            permissionRepository.save(permission);
        }
    }

    private void seedRoles() {
        String[] requester = {"ticket:create", "ticket:view", "ticket:close"};
        String[] agent = {"ticket:view", "ticket:classify", "ticket:resolve"};
        String[] supervisor = {"ticket:view", "ticket:classify", "ticket:assign", "ticket:resolve",
                "ticket:close", "approval:decide", "sla:view", "stats:view"};
        String[] admin = {"ticket:create", "ticket:view", "ticket:classify", "ticket:assign",
                "ticket:resolve", "ticket:close", "approval:decide", "config:write",
                "sla:view", "audit:view", "stats:view"};
        saveRole("REQUESTER", "普通员工", "自助提单与确认", requester);
        saveRole("AGENT", "处理人", "处理分配工单", agent);
        saveRole("SUPERVISOR", "主管", "分类、分配与审批", supervisor);
        saveRole("ADMIN", "管理员", "全平台配置与管理", admin);
    }

    private void saveRole(String code, String name, String description, String[] permissionCodes) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        for (String permissionCode : permissionCodes) {
            permissionRepository.findByCode(permissionCode)
                    .ifPresent(p -> role.getPermissions().add(p));
        }
        roleRepository.save(role);
    }

    private Map<String, Dept> seedDepts() {
        Map<String, Dept> map = new HashMap<>();
        map.put("IT", saveDept("IT", "IT部", 1));
        map.put("ADMIN", saveDept("ADMIN", "行政部", 2));
        map.put("HR", saveDept("HR", "人事部", 3));
        map.put("EQUIPMENT", saveDept("EQUIPMENT", "设备部", 4));
        return map;
    }

    private Dept saveDept(String code, String name, int sortOrder) {
        Dept dept = new Dept();
        dept.setCode(code);
        dept.setName(name);
        dept.setParentId(null);
        dept.setSortOrder(sortOrder);
        return deptRepository.save(dept);
    }

    private Map<String, User> seedUsers(Map<String, Dept> depts) {
        Map<String, User> map = new HashMap<>();
        map.put("admin", saveUser("admin", "admin123", "系统管理员", "admin@nexflow.local", depts.get("IT"), "ADMIN", null));
        map.put("supervisor", saveUser("supervisor", "sup123", "陈晨", "chenchen@nexflow.local", depts.get("IT"), "SUPERVISOR", null));
        map.put("wangqiang", saveUser("wangqiang", "wq123", "王强", "wangqiang@nexflow.local", depts.get("IT"), "AGENT", "IT"));
        map.put("limin", saveUser("limin", "lm123", "李娜", "limin@nexflow.local", depts.get("ADMIN"), "AGENT", "行政"));
        map.put("zhangting", saveUser("zhangting", "zt123", "张婷", "zhangting@nexflow.local", depts.get("HR"), "AGENT", "人事"));
        map.put("zhaolei", saveUser("zhaolei", "zl123", "赵磊", "zhaolei@nexflow.local", depts.get("EQUIPMENT"), "AGENT", "设备"));
        map.put("linxiao", saveUser("linxiao", "lx123", "林晓", "linxiao@nexflow.local", depts.get("HR"), "REQUESTER", null));
        map.put("wangfang", saveUser("wangfang", "wf123", "王芳", "wangfang@nexflow.local", depts.get("IT"), "REQUESTER", null));
        return map;
    }

    private User saveUser(String username, String rawPassword, String displayName, String email,
                          Dept dept, String roleCode, String skillTag) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setDeptId(dept.getId());
        user.setSkillTag(skillTag);
        roleRepository.findByCode(roleCode).ifPresent(r -> user.getRoles().add(r));
        return userRepository.save(user);
    }

    private Map<String, SlaPolicy> seedSlaPolicies() {
        Map<String, SlaPolicy> map = new HashMap<>();
        map.put("SLA_IT", savePolicy("SLA_IT", "标准IT支持", 120, 480));
        map.put("SLA_ADMIN", savePolicy("SLA_ADMIN", "行政服务", 240, 1440));
        map.put("SLA_HR", savePolicy("SLA_HR", "人事服务", 240, 2880));
        map.put("SLA_EQUIP", savePolicy("SLA_EQUIP", "设备维护", 120, 960));
        map.put("SLA_ACCOUNT", savePolicy("SLA_ACCOUNT", "账号权限", 120, 240));
        return map;
    }

    private SlaPolicy savePolicy(String code, String name, int firstResponseMinutes, int resolveMinutes) {
        SlaPolicy policy = new SlaPolicy();
        policy.setCode(code);
        policy.setName(name);
        policy.setFirstResponseMinutes(firstResponseMinutes);
        policy.setResolveMinutes(resolveMinutes);
        policy.setBusinessCalendarJson(SlaCalculator.DEFAULT_CALENDAR);
        return slaPolicyRepository.save(policy);
    }

    private void seedHolidays() {
        saveHoliday(LocalDate.of(LocalDate.now().getYear(), 1, 1), "元旦");
        saveHoliday(LocalDate.of(LocalDate.now().getYear(), 10, 1), "国庆节");
        saveHoliday(LocalDate.of(LocalDate.now().getYear(), 10, 2), "国庆节");
    }

    private void saveHoliday(LocalDate date, String name) {
        Holiday holiday = new Holiday();
        holiday.setDate(date);
        holiday.setName(name);
        holidayRepository.save(holiday);
    }

    private Map<String, TicketType> seedTicketTypes(Map<String, SlaPolicy> policies) {
        Map<String, TicketType> map = new HashMap<>();
        TicketType it = saveType("IT_SUPPORT", "IT支持", "网络、软件、电脑与账号类IT问题", false, false, policies.get("SLA_IT"), null);
        addFields(it, new Object[][]{
                {"problemType", "问题类型", FieldType.SELECT, true, "[{\"label\":\"网络\",\"value\":\"网络\"},{\"label\":\"硬件\",\"value\":\"硬件\"},{\"label\":\"软件\",\"value\":\"软件\"},{\"label\":\"账号\",\"value\":\"账号\"}]", 1},
                {"impactScope", "影响范围", FieldType.SELECT, true, "[{\"label\":\"仅本人\",\"value\":\"仅本人\"},{\"label\":\"多人\",\"value\":\"多人\"},{\"label\":\"全公司\",\"value\":\"全公司\"}]", 2},
                {"occurTime", "发生时间", FieldType.DATE, false, null, 3},
                {"canRemote", "是否可远程", FieldType.BOOLEAN, false, null, 4},
        });
        map.put("IT_SUPPORT", it);

        TicketType admin = saveType("ADMIN_SERVICE", "行政服务", "办公用品、会议室、快递等行政事务", false, false, policies.get("SLA_ADMIN"), null);
        addFields(admin, new Object[][]{
                {"serviceType", "服务类型", FieldType.SELECT, true, "[{\"label\":\"办公用品\",\"value\":\"办公用品\"},{\"label\":\"会议室\",\"value\":\"会议室\"},{\"label\":\"快递\",\"value\":\"快递\"},{\"label\":\"车辆\",\"value\":\"车辆\"}]", 1},
                {"quantity", "数量", FieldType.NUMBER, false, null, 2},
                {"purpose", "用途说明", FieldType.TEXT, false, null, 3},
                {"expectDate", "期望送达日期", FieldType.DATE, false, null, 4},
        });
        map.put("ADMIN_SERVICE", admin);

        TicketType hr = saveType("HR_SERVICE", "人事服务", "入职、离职、社保、证明等人事事项", false, false, policies.get("SLA_HR"), null);
        addFields(hr, new Object[][]{
                {"serviceType", "事项类型", FieldType.SELECT, true, "[{\"label\":\"入职\",\"value\":\"入职\"},{\"label\":\"离职\",\"value\":\"离职\"},{\"label\":\"社保公积金\",\"value\":\"社保公积金\"},{\"label\":\"在职证明\",\"value\":\"在职证明\"}]", 1},
                {"contactPerson", "对接人", FieldType.USER, false, null, 2},
                {"remark", "说明", FieldType.TEXT, false, null, 3},
                {"urgency", "紧急度", FieldType.SELECT, false, "[{\"label\":\"低\",\"value\":\"低\"},{\"label\":\"中\",\"value\":\"中\"},{\"label\":\"高\",\"value\":\"高\"}]", 4},
        });
        map.put("HR_SERVICE", hr);

        TicketType equip = saveType("EQUIPMENT_MAINTENANCE", "设备维护", "打印机、投影、门禁等设备故障报修", false, false, policies.get("SLA_EQUIP"), null);
        addFields(equip, new Object[][]{
                {"deviceType", "设备类型", FieldType.SELECT, true, "[{\"label\":\"打印机\",\"value\":\"打印机\"},{\"label\":\"投影\",\"value\":\"投影\"},{\"label\":\"门禁\",\"value\":\"门禁\"},{\"label\":\"空调\",\"value\":\"空调\"}]", 1},
                {"faultDesc", "故障描述", FieldType.TEXT, true, null, 2},
                {"affectWork", "是否影响生产", FieldType.BOOLEAN, false, null, 3},
                {"reportDate", "报修日期", FieldType.DATE, false, null, 4},
        });
        map.put("EQUIPMENT_MAINTENANCE", equip);

        TicketType account = saveType("ACCOUNT_PERMISSION", "账号权限", "账号开通、权限变更、注销等需审批事项", true, true, policies.get("SLA_ACCOUNT"), null);
        addFields(account, new Object[][]{
                {"applyType", "申请类型", FieldType.SELECT, true, "[{\"label\":\"新建账号\",\"value\":\"新建账号\"},{\"label\":\"权限变更\",\"value\":\"权限变更\"},{\"label\":\"重置密码\",\"value\":\"重置密码\"},{\"label\":\"离职注销\",\"value\":\"离职注销\"}]", 1},
                {"system", "涉及系统", FieldType.SELECT, true, "[{\"label\":\"OA\",\"value\":\"OA\"},{\"label\":\"ERP\",\"value\":\"ERP\"},{\"label\":\"邮箱\",\"value\":\"邮箱\"},{\"label\":\"数据库\",\"value\":\"数据库\"}]", 2},
                {"permissionLevel", "权限级别", FieldType.SELECT, false, "[{\"label\":\"只读\",\"value\":\"只读\"},{\"label\":\"普通\",\"value\":\"普通\"},{\"label\":\"管理\",\"value\":\"管理\"}]", 3},
                {"effectiveDate", "生效日期", FieldType.DATE, false, null, 4},
                {"reason", "申请理由", FieldType.TEXT, true, null, 5},
        });
        map.put("ACCOUNT_PERMISSION", account);
        return map;
    }

    private TicketType saveType(String code, String name, String description, boolean requiresApproval,
                                boolean autoAssign, SlaPolicy policy, String allowedDeptIds) {
        TicketType type = new TicketType();
        type.setCode(code);
        type.setName(name);
        type.setDescription(description);
        type.setActive(true);
        type.setRequiresApproval(requiresApproval);
        type.setAutoAssign(autoAssign);
        type.setSlaPolicyId(policy != null ? policy.getId() : null);
        type.setAllowedDeptIds(allowedDeptIds);
        return ticketTypeRepository.save(type);
    }

    private void addFields(TicketType type, Object[][] fields) {
        int i = 1;
        for (Object[] field : fields) {
            CustomFieldDef def = new CustomFieldDef();
            def.setTypeId(type.getId());
            def.setFieldCode((String) field[0]);
            def.setFieldName((String) field[1]);
            def.setFieldType((FieldType) field[2]);
            def.setRequired((Boolean) field[3]);
            def.setOptionsJson((String) field[4]);
            def.setSortOrder(i++);
            customFieldDefRepository.save(def);
        }
    }

    private void seedRules(Map<String, Dept> depts, Map<String, TicketType> types) {
        saveRule("IT支持轮转", 1, types.get("IT_SUPPORT").getId(), depts.get("IT").getId(),
                AssignmentStrategy.LEAST_WORKLOAD, 5, "IT");
        saveRule("行政服务轮转", 2, types.get("ADMIN_SERVICE").getId(), depts.get("ADMIN").getId(),
                AssignmentStrategy.ROUND_ROBIN, 5, "行政");
        saveRule("设备维护技能优先", 3, types.get("EQUIPMENT_MAINTENANCE").getId(), depts.get("EQUIPMENT").getId(),
                AssignmentStrategy.MOST_SKILLED, 6, "设备");
    }

    private void saveRule(String name, int priority, Long typeId, Long scopeDeptId,
                          AssignmentStrategy strategy, int workloadLimit, String skillTag) {
        AssignmentRule rule = new AssignmentRule();
        rule.setName(name);
        rule.setActive(true);
        rule.setPriority(priority);
        rule.setTicketTypeId(typeId);
        rule.setScopeDeptId(scopeDeptId);
        rule.setStrategy(strategy);
        rule.setWorkloadLimit(workloadLimit);
        rule.setSkillTag(skillTag);
        assignmentRuleRepository.save(rule);
    }

    private void seedFaqs() {
        saveFaq("如何重置邮箱密码？", "请提交「账号权限」类工单，选择「重置密码」，审批通过后处理人会指引您完成重置。", "账号", 1);
        saveFaq("打印机卡纸如何快速处理？", "请先按设备面板提示取出卡纸；如反复出现，提交「设备维护」类工单并描述故障。", "设备", 2);
        saveFaq("申请办公用品需要审批吗？", "常规办公用品无需审批，提交「行政服务」工单后由行政部处理；大宗采购需主管审批。", "行政", 3);
        saveFaq("入职手续需要准备什么材料？", "入职当天携带身份证与学历证明，提交「人事服务」工单选择「入职」即可。", "人事", 4);
        saveFaq("工单多久能得到响应？", "IT类工单承诺 2 小时内首次响应，4 个工作小时内给出解决方案，具体以 SLA 策略为准。", "SLA", 5);
        saveFaq("如何查询我的工单进度？", "登录员工门户后，在「我的工单」中可实时查看状态、处理人与 SLA 剩余时间。", "使用", 6);
    }

    private void saveFaq(String question, String answer, String category, int sortOrder) {
        FaqEntry faq = new FaqEntry();
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setCategory(category);
        faq.setSortOrder(sortOrder);
        faqEntryRepository.save(faq);
    }

    // ---------- 演示工单 ----------

    private void seedTickets(Map<String, TicketType> types, Map<String, Dept> depts, Map<String, User> users) {
        TicketType it = types.get("IT_SUPPORT");
        TicketType admin = types.get("ADMIN_SERVICE");
        TicketType hr = types.get("HR_SERVICE");
        TicketType equip = types.get("EQUIPMENT_MAINTENANCE");
        TicketType account = types.get("ACCOUNT_PERMISSION");

        User linxiao = users.get("linxiao");
        User wangfang = users.get("wangfang");
        User wangqiang = users.get("wangqiang");
        User limin = users.get("limin");
        User zhangting = users.get("zhangting");
        User zhaolei = users.get("zhaolei");
        User chenchen = users.get("supervisor");

        // 已关闭 12 张
        createTicket(it, TicketState.CLOSED, linxiao, wangqiang, depts.get("HR"), 28, 26,
                Priority.HIGH, "办公区 WiFi 频繁断连", "3 楼东侧办公区 WiFi 每 30 分钟断连一次，影响线上会议。",
                "已更换故障 AP，网络恢复正常。", 1, "网络", "多人");
        createTicket(it, TicketState.CLOSED, wangfang, wangqiang, depts.get("IT"), 25, 23,
                Priority.MEDIUM, "邮箱无法收发海外邮件", "发送到海外客户的邮件被退回，报错 550。",
                "已联系服务商解除限制并完成验证。", 2, "邮箱", "仅本人");
        createTicket(admin, TicketState.CLOSED, linxiao, limin, depts.get("HR"), 22, 20,
                Priority.LOW, "申请领用会议室白板笔", "5 楼培训室白板笔用完，申请领用 2 盒。",
                "已发放至前台自取。", 1, "办公用品", null);
        createTicket(hr, TicketState.CLOSED, wangfang, zhangting, depts.get("IT"), 19, 17,
                Priority.HIGH, "开具在职证明（出国签证用）", "需要中英文双语在职证明，用于办理日本签证。",
                "已开具并盖章，请到人事部领取。", 1, "在职证明", null);
        createTicket(equip, TicketState.CLOSED, linxiao, zhaolei, depts.get("HR"), 16, 14,
                Priority.MEDIUM, "3 楼打印机频繁卡纸", "HP 打印机每天卡纸 3-4 次，已按提示清理无效。",
                "已更换搓纸轮，测试正常。", 1, "打印机", "是");
        createTicket(it, TicketState.CLOSED, linxiao, wangqiang, depts.get("HR"), 13, 11,
                Priority.URGENT, "财务系统无法登录", "财务系统提示密码错误，但密码未修改过。",
                "已重置密码并验证登录。", 1, "账号", "仅本人");
        createTicket(admin, TicketState.CLOSED, wangfang, limin, depts.get("IT"), 10, 8,
                Priority.MEDIUM, "申请新员工工位布置", "下周一有 2 名新员工入职，需布置 6 楼工位。",
                "已完成工位布置与办公用品配备。", 1, "办公用品", null);
        createTicket(hr, TicketState.CLOSED, linxiao, zhangting, depts.get("HR"), 8, 6,
                Priority.MEDIUM, "社保公积金基数调整咨询", "新年度社保基数调整，希望确认个人部分变化。",
                "已电话说明调整明细。", 1, "社保公积金", null);
        createTicket(equip, TicketState.CLOSED, wangfang, zhaolei, depts.get("IT"), 6, 4,
                Priority.LOW, "会议室投影仪颜色偏色", "2 号会议室投影仪画面偏黄，影响演示。",
                "已校准投影仪色彩。", 1, "投影", null);
        createTicket(it, TicketState.CLOSED, linxiao, wangqiang, depts.get("HR"), 4, 2,
                Priority.HIGH, "OA 系统审批流卡顿", "提交请假审批后长时间无响应，页面转圈。",
                "已重启 OA 应用服务并清理缓存。", 1, "软件", "多人");
        createTicket(admin, TicketState.CLOSED, linxiao, limin, depts.get("HR"), 3, 1,
                Priority.MEDIUM, "申请会议室(2026-10-15 上午)", "需要 5 楼大会议室，容纳 20 人，半天。",
                "已确认预定。", 1, "会议室", null);
        createTicket(it, TicketState.CLOSED, wangfang, wangqiang, depts.get("IT"), 2, 1,
                Priority.LOW, "笔记本电池续航下降", "使用一年后电池续航从 6 小时降至 2 小时。",
                "已更换电池。", 1, "硬件", "仅本人");

        // 已解决（待确认）3 张
        createTicket(it, TicketState.RESOLVED, linxiao, wangqiang, depts.get("HR"), 2, 0,
                Priority.HIGH, "VPN 无法连接到公司内网", "居家办公 VPN 连接超时，重试多次无效。",
                "已调整 VPN 网关配置并验证连接。", 1, "网络", "多人");
        createTicket(equip, TicketState.RESOLVED, wangfang, zhaolei, depts.get("IT"), 1, 0,
                Priority.MEDIUM, "门禁卡无法开门", "6 楼西侧门禁刷卡无反应。",
                "已重新授权门禁卡。", 1, "门禁", null);
        createTicket(hr, TicketState.RESOLVED, linxiao, zhangting, depts.get("HR"), 1, 0,
                Priority.LOW, "打印工资单需求", "需要上月工资单打印版用于办理贷款。",
                "已提供电子版，可自行打印。", 1, "在职证明", null);

        // 处理中 4 张
        createTicket(it, TicketState.IN_PROGRESS, wangfang, wangqiang, depts.get("IT"), 3, -1,
                Priority.URGENT, "ERP 系统报表导出失败", "销售月度报表导出一直报超时错误。",
                null, 1, "软件", "多人");
        createTicket(admin, TicketState.IN_PROGRESS, linxiao, limin, depts.get("HR"), 2, -1,
                Priority.MEDIUM, "新员工入职礼包申请", "下周入职 3 人，需要准备入职礼包。",
                null, 1, "办公用品", null);
        createTicket(equip, TicketState.IN_PROGRESS, linxiao, zhaolei, depts.get("HR"), 1, -1,
                Priority.HIGH, "空调制冷异常", "4 楼东区空调制冷效果差，室温 30 度。",
                null, 1, "空调", "是");
        createTicket(it, TicketState.IN_PROGRESS, wangfang, wangqiang, depts.get("IT"), 1, -1,
                Priority.MEDIUM, "新装软件授权申请", "设计部需要安装 Adobe 全家桶授权。",
                null, 1, "软件", "多人");

        // 已分配 3 张
        createTicket(it, TicketState.ASSIGNED, linxiao, wangqiang, depts.get("HR"), 1, -1,
                Priority.MEDIUM, "视频会议回声问题", "会议室视频会议设备存在明显回声。",
                null, 1, "硬件", "多人");
        createTicket(hr, TicketState.ASSIGNED, linxiao, zhangting, depts.get("HR"), 1, -1,
                Priority.LOW, "劳动合同复印件申请", "需要劳动合同复印件用于公积金提取。",
                null, 1, "在职证明", null);
        createTicket(admin, TicketState.ASSIGNED, wangfang, limin, depts.get("IT"), 1, -1,
                Priority.MEDIUM, "快递代收登记", "前台代收私人快递需登记，建议规则提醒。",
                null, 1, "快递", null);

        // 已分类 2 张
        createTicket(it, TicketState.CLASSIFIED, linxiao, null, depts.get("HR"), 0, -1,
                Priority.HIGH, "公司邮箱存储空间已满", "邮箱提示 95% 已满，无法发送大附件。",
                null, 1, "账号", "仅本人");
        createTicket(equip, TicketState.CLASSIFIED, wangfang, null, depts.get("IT"), 0, -1,
                Priority.LOW, "工位键盘需要更换", "键盘部分按键失灵，申请更换。",
                null, 1, "打印机", null);

        // 已创建 2 张
        createTicket(hr, TicketState.CREATED, wangfang, null, depts.get("IT"), 0, -1,
                Priority.MEDIUM, "哺乳假政策咨询", "希望了解公司哺乳假申请流程与所需材料。",
                null, 1, "入职", null);
        createTicket(it, TicketState.CREATED, linxiao, null, depts.get("HR"), 0, -1,
                Priority.LOW, "双屏显示器支架申请", "希望申请一个双屏显示器支架提升工作效率。",
                null, 1, "硬件", "仅本人");

        // 待审批 2 张（ACCOUNT_PERMISSION）
        createTicket(account, TicketState.PENDING_APPROVAL, linxiao, null, depts.get("HR"), 1, -1,
                Priority.HIGH, "申请数据库只读账号", "数据分析需要连接生产库只读账号，权限级别只读。",
                null, 1, "新建账号", "数据库");
        createTicket(account, TicketState.PENDING_APPROVAL, wangfang, null, depts.get("IT"), 0, -1,
                Priority.URGENT, "ERP 权限变更（销售模块）", "转岗销售部，需要 ERP 销售模块读写权限。",
                null, 1, "权限变更", "ERP");

        // 已取消 2 张
        createTicket(admin, TicketState.CANCELED, linxiao, null, depts.get("HR"), 5, -1,
                Priority.LOW, "申请搬迁工位", "原工位调整计划取消。",
                null, 1, "办公用品", null);
        createTicket(it, TicketState.CANCELED, wangfang, null, depts.get("IT"), 3, -1,
                Priority.MEDIUM, "申请笔记本内存升级", "更换新笔记本后无需升级。",
                null, 1, "硬件", "仅本人");

        // 已审批通过的账号权限工单 1 张（APPROVED + 已分配）
        createApprovedAccountTicket(account, depts.get("HR"), linxiao, wangqiang, chenchen, 7);

        // 已驳回的账号权限工单 1 张
        createRejectedAccountTicket(account, depts.get("HR"), linxiao, chenchen, 4);
    }

    private final Map<String, AtomicInteger> ticketNoCounters = new HashMap<>();

    private Ticket createTicket(TicketType type, TicketState status, User creator, User assignee,
                                Dept dept, int createdDaysAgo, int resolvedDaysAgo, Priority priority,
                                String title, String description, String resolution,
                                int comments, String f1, String f2) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(createdDaysAgo)
                .withHour(9 + createdDaysAgo % 8).withMinute(15);
        Ticket ticket = new Ticket();
        ticket.setTicketNo(nextTicketNo(createdAt));
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setTypeId(type.getId());
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setCreatorId(creator.getId());
        ticket.setAssigneeId(assignee != null ? assignee.getId() : null);
        ticket.setGroupDeptId(dept.getId());
        ticket.setDemo(true);
        ticket.setResolution(resolution);
        ticket.setCreatedAt(createdAt);
        if (status == TicketState.RESOLVED || status == TicketState.CLOSED) {
            ticket.setResolvedAt(createdAt.plusDays(resolvedDaysAgo >= 0 ? resolvedDaysAgo : 1L)
                    .withHour(16).withMinute(30));
        }
        if (status == TicketState.CLOSED) {
            ticket.setClosedAt(ticket.getResolvedAt().plusHours(2));
        }
        ticketRepository.save(ticket);

        // 自定义字段值
        List<CustomFieldDef> defs = customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(type.getId());
        if (!defs.isEmpty() && f1 != null) {
            customFieldValueRepository.save(new TicketCustomFieldValue(ticket.getId(), defs.get(0).getId(), f1));
        }
        if (defs.size() > 1 && f2 != null) {
            customFieldValueRepository.save(new TicketCustomFieldValue(ticket.getId(), defs.get(1).getId(), f2));
        }

        // 状态历史
        history(ticket, null, TicketState.CREATED, "create", creator.getId(), "提交工单");
        if (status != TicketState.CREATED) {
            User classifier = assignee != null ? assignee : creator;
            history(ticket, TicketState.CREATED, TicketState.CLASSIFIED, "classify", classifier.getId(), "分类："
                    + type.getName());
        }
        if (status == TicketState.PENDING_APPROVAL) {
            // 处于"审批中"的工单必须存在对应的待审批记录：
            // 否则工单会卡死（可执行操作只剩取消），审批中心也会是空的。
            User approver = findSupervisor(dept.getId());
            ApprovalRequest approval = new ApprovalRequest();
            approval.setTicketId(ticket.getId());
            approval.setApproverId(approver != null ? approver.getId() : null);
            approval.setApproverRole("SUPERVISOR");
            approval.setStatus(ApprovalStatus.PENDING);
            approval.setRequestedAt(createdAt.plusMinutes(30));
            approvalRepository.save(approval);
            history(ticket, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL, "request_approval",
                    approver != null ? approver.getId() : creator.getId(), "该类型工单需审批，已提交审批");
        }
        if (status == TicketState.ASSIGNED || status == TicketState.IN_PROGRESS
                || status == TicketState.RESOLVED || status == TicketState.CLOSED) {
            history(ticket, TicketState.CLASSIFIED, TicketState.ASSIGNED, "assign",
                    classifierId(assignee, creator), "分配：" + (assignee != null ? assignee.getDisplayName() : ""));
            addAssignmentRecord(ticket, assignee, "规则「" + type.getName() + "轮转」命中，自动分配",
                    AssignmentMode.AUTO, 0L);
        }
        if (status == TicketState.IN_PROGRESS || status == TicketState.RESOLVED || status == TicketState.CLOSED) {
            history(ticket, TicketState.ASSIGNED, TicketState.IN_PROGRESS, "start",
                    assignee != null ? assignee.getId() : creator.getId(), "开始处理");
        }
        if (status == TicketState.RESOLVED || status == TicketState.CLOSED) {
            history(ticket, TicketState.IN_PROGRESS, TicketState.RESOLVED, "resolve",
                    assignee != null ? assignee.getId() : creator.getId(), resolution);
        }
        if (status == TicketState.CLOSED) {
            history(ticket, TicketState.RESOLVED, TicketState.CLOSED, "confirm", creator.getId(), "确认关闭");
        }
        if (status == TicketState.CANCELED) {
            history(ticket, TicketState.CREATED, TicketState.CANCELED, "cancel", creator.getId(), "创建人取消");
        }

        // 评论
        for (int i = 1; i <= comments; i++) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setAuthorId(assignee != null ? assignee.getId() : creator.getId());
            comment.setContent("演示评论 " + i + "：工单「" + title + "」的处理进展说明。");
            comment.setInternal(false);
            comment.setCreatedAt(createdAt.plusHours(i));
            commentRepository.save(comment);
        }
        return ticket;
    }

    private Long classifierId(User assignee, User creator) {
        return assignee != null ? assignee.getId() : creator.getId();
    }

    /**
     * 找该部门的主管作为审批人；部门内没有主管时退回任意在职主管。
     * 用于给"审批中"的演示工单补上对应的待审批记录。
     */
    private User findSupervisor(Long deptId) {
        if (deptId != null) {
            User inDept = userRepository.findByDeptIdOrderByIdAsc(deptId).stream()
                    .filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getCode())))
                    .findFirst()
                    .orElse(null);
            if (inDept != null) {
                return inDept;
            }
        }
        return userRepository.findByStatusOrderByIdAsc(UserStatus.ACTIVE).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getCode())))
                .findFirst()
                .orElse(null);
    }

    private void createApprovedAccountTicket(TicketType account, Dept dept, User creator, User assignee,
                                             User approver, int daysAgo) {
        Ticket ticket = createTicket(account, TicketState.ASSIGNED, creator, assignee, dept, daysAgo, -1,
                Priority.HIGH, "申请 OA 新建账号", "新员工入职，需要创建 OA 账号并加入对应组织架构。",
                null, 1, "新建账号", "OA");
        history(ticket, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL, "request_approval",
                approver.getId(), "该类型工单需审批，已提交审批");
        ApprovalRequest approval = new ApprovalRequest();
        approval.setTicketId(ticket.getId());
        approval.setApproverId(approver.getId());
        approval.setApproverRole("SUPERVISOR");
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setComment("同意创建");
        approval.setRequestedAt(ticket.getCreatedAt());
        approval.setDecidedAt(ticket.getCreatedAt().plusHours(3));
        approvalRepository.save(approval);
        history(ticket, TicketState.PENDING_APPROVAL, TicketState.ASSIGNED, "approve",
                approver.getId(), "审批通过，自动分配");
        addAssignmentRecord(ticket, assignee, "审批通过后自动分配", AssignmentMode.AUTO, 0L);
    }

    private void createRejectedAccountTicket(TicketType account, Dept dept, User creator, User approver, int daysAgo) {
        Ticket ticket = createTicket(account, TicketState.CLASSIFIED, creator, null, dept, daysAgo, -1,
                Priority.MEDIUM, "申请数据库管理权限", "数据分析需要生产数据库管理权限。",
                null, 1, "权限变更", "数据库");
        history(ticket, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL, "request_approval",
                approver.getId(), "该类型工单需审批，已提交审批");
        ApprovalRequest approval = new ApprovalRequest();
        approval.setTicketId(ticket.getId());
        approval.setApproverId(approver.getId());
        approval.setApproverRole("SUPERVISOR");
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setComment("管理权限级别过高，请改为只读后重新提交");
        approval.setRequestedAt(ticket.getCreatedAt());
        approval.setDecidedAt(ticket.getCreatedAt().plusHours(2));
        approvalRepository.save(approval);
        history(ticket, TicketState.PENDING_APPROVAL, TicketState.CLASSIFIED, "reject",
                approver.getId(), "审批未通过，已退回分类");
    }

    private void addAssignmentRecord(Ticket ticket, User assignee, String reason,
                                     AssignmentMode mode, Long assignedById) {
        AssignmentRecord record = new AssignmentRecord();
        record.setTicketId(ticket.getId());
        record.setRuleName(ticketTypeRepository.findById(ticket.getTypeId()).map(TicketType::getName).orElse(null));
        record.setReason(reason);
        record.setAssignedUserId(assignee != null ? assignee.getId() : null);
        record.setAssignedById(assignedById);
        record.setMode(mode);
        record.setCreatedAt(ticket.getCreatedAt().plusMinutes(5));
        assignmentRecordRepository.save(record);
    }

    private void history(Ticket ticket, TicketState from, TicketState to, String action,
                         Long operatorId, String note) {
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setAction(action);
        history.setOperatorId(operatorId);
        history.setNote(note);
        history.setCreatedAt(ticket.getCreatedAt().plusMinutes(1));
        historyRepository.save(history);
    }

    private String nextTicketNo(LocalDateTime createdAt) {
        String prefix = "NF-" + createdAt.toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        AtomicInteger counter = ticketNoCounters.computeIfAbsent(prefix, k -> {
            int max = ticketRepository.findTopByTicketNoStartingWithOrderByTicketNoDesc(prefix)
                    .map(t -> Integer.parseInt(t.getTicketNo().substring(t.getTicketNo().length() - 4)))
                    .orElse(0);
            return new AtomicInteger(max);
        });
        return prefix + "-" + String.format("%04d", counter.incrementAndGet());
    }
}
