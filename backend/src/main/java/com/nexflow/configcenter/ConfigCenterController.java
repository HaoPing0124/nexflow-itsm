package com.nexflow.configcenter;

import com.nexflow.audit.Audit;
import com.nexflow.auth.UserDto;
import com.nexflow.common.ApiResponse;
import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.common.PageResult;
import com.nexflow.sla.Holiday;
import com.nexflow.sla.HolidayRepository;
import com.nexflow.sla.SlaPolicy;
import com.nexflow.sla.SlaPolicyRepository;
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
import com.nexflow.user.UserService;
import com.nexflow.user.UserStatus;
import com.nexflow.workflow.TicketState;
import com.nexflow.workflow.TransitionAction;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigCenterController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DeptRepository deptRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CustomFieldDefRepository customFieldDefRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final HolidayRepository holidayRepository;

    // ---------- 用户 ----------

    @GetMapping("/users")
    public ApiResponse<PageResult<UserDto>> users(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "100") int size) {
        CurrentUser.requirePermission("config:write");
        List<User> all = userRepository.findAll();
        List<User> sorted = all.stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
        int from = Math.min((page - 1) * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        Map<Long, String> deptNames = deptRepository.findAll().stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        List<UserDto> items = sorted.subList(from, to).stream()
                .map(u -> UserDto.from(u, u.getDeptId() != null ? deptNames.get(u.getDeptId()) : null))
                .toList();
        return ApiResponse.ok(PageResult.of(items, sorted.size(), page, size));
    }

    @PostMapping("/users")
    @Audit(action = "config.user.create", module = "config")
    public ApiResponse<UserDto> createUser(@RequestBody UserBody body) {
        CurrentUser.requirePermission("config:write");
        if (body.username() == null || body.username().isBlank() || body.password() == null || body.password().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        User user = userService.createUser(body.username().trim(), body.password(), body.displayName(),
                body.email(), body.deptId(), body.roles(), body.status());
        return ApiResponse.ok(UserDto.from(userService.loadUser(user.getId()), deptName(user.getDeptId())));
    }

    @PutMapping("/users/{id}")
    @Audit(action = "config.user.update", module = "config")
    public ApiResponse<UserDto> updateUser(@PathVariable Long id, @RequestBody UserBody body) {
        CurrentUser.requirePermission("config:write");
        User user = userService.updateUser(id, body.password(), body.displayName(), body.email(),
                body.deptId(), body.roles(), body.status());
        return ApiResponse.ok(UserDto.from(userService.loadUser(user.getId()), deptName(user.getDeptId())));
    }

    @DeleteMapping("/users/{id}")
    @Audit(action = "config.user.delete", module = "config")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        User user = userService.loadUser(id);
        // 保留历史引用，采用停用而非物理删除
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        return ApiResponse.ok();
    }

    // ---------- 部门 ----------

    @GetMapping("/depts")
    public ApiResponse<List<Dept>> depts() {
        CurrentUser.requirePermission("config:write");
        return ApiResponse.ok(deptRepository.findAllByOrderBySortOrderAsc());
    }

    @PostMapping("/depts")
    @Audit(action = "config.dept.create", module = "config")
    public ApiResponse<Dept> createDept(@RequestBody DeptBody body) {
        CurrentUser.requirePermission("config:write");
        Dept dept = new Dept();
        applyDept(dept, body);
        return ApiResponse.ok(deptRepository.save(dept));
    }

    @PutMapping("/depts/{id}")
    @Audit(action = "config.dept.update", module = "config")
    public ApiResponse<Dept> updateDept(@PathVariable Long id, @RequestBody DeptBody body) {
        CurrentUser.requirePermission("config:write");
        Dept dept = deptRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "部门不存在"));
        applyDept(dept, body);
        return ApiResponse.ok(deptRepository.save(dept));
    }

    @DeleteMapping("/depts/{id}")
    @Audit(action = "config.dept.delete", module = "config")
    public ApiResponse<Void> deleteDept(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        if (userRepository.findByDeptIdOrderByIdAsc(id).stream().anyMatch(User::isActive)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门下存在在职用户，不能删除");
        }
        deptRepository.deleteById(id);
        return ApiResponse.ok();
    }

    // ---------- 角色 ----------

    @GetMapping("/roles")
    public ApiResponse<List<RoleDto>> roles() {
        CurrentUser.requirePermission("config:write");
        List<RoleDto> result = new ArrayList<>();
        for (Role role : roleRepository.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList()) {
            RoleDto dto = new RoleDto();
            dto.setCode(role.getCode());
            dto.setName(role.getName());
            dto.setDescription(role.getDescription());
            List<PermissionDto> permissions = role.getPermissions().stream()
                    .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                    .map(p -> {
                        PermissionDto pd = new PermissionDto();
                        pd.setCode(p.getCode());
                        pd.setName(p.getName());
                        pd.setModule(p.getModule());
                        return pd;
                    }).toList();
            dto.setPermissions(permissions);
            result.add(dto);
        }
        return ApiResponse.ok(result);
    }

    // ---------- 工单类型 ----------

    @GetMapping("/ticket-types")
    public ApiResponse<List<TicketTypeDto>> ticketTypes() {
        CurrentUser.requirePermission("config:write");
        return ApiResponse.ok(toTypeDtos(ticketTypeRepository.findAllByOrderByIdAsc()));
    }

    @PostMapping("/ticket-types")
    @Audit(action = "config.ticket-type.create", module = "config")
    public ApiResponse<TicketTypeDto> createTicketType(@RequestBody TicketTypeBody body) {
        CurrentUser.requirePermission("config:write");
        TicketType type = new TicketType();
        applyType(type, body);
        TicketType saved = ticketTypeRepository.save(type);
        return ApiResponse.ok(toTypeDtos(List.of(saved)).get(0));
    }

    @PutMapping("/ticket-types/{id}")
    @Audit(action = "config.ticket-type.update", module = "config")
    public ApiResponse<TicketTypeDto> updateTicketType(@PathVariable Long id, @RequestBody TicketTypeBody body) {
        CurrentUser.requirePermission("config:write");
        TicketType type = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单类型不存在"));
        applyType(type, body);
        TicketType saved = ticketTypeRepository.save(type);
        return ApiResponse.ok(toTypeDtos(List.of(saved)).get(0));
    }

    @DeleteMapping("/ticket-types/{id}")
    @Audit(action = "config.ticket-type.delete", module = "config")
    public ApiResponse<Void> deleteTicketType(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        ticketTypeRepository.deleteById(id);
        customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(id).forEach(customFieldDefRepository::delete);
        return ApiResponse.ok();
    }

    @GetMapping("/ticket-types/{id}/fields")
    public ApiResponse<List<FieldDefDto>> fields(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        return ApiResponse.ok(customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(id).stream()
                .map(ConfigCenterController::toFieldDto).toList());
    }

    @PostMapping("/ticket-types/{id}/fields")
    @Audit(action = "config.field.create", module = "config")
    public ApiResponse<List<FieldDefDto>> addField(@PathVariable Long id, @RequestBody FieldDefBody body) {
        CurrentUser.requirePermission("config:write");
        if (!ticketTypeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单类型不存在");
        }
        if (body.fieldCode() == null || body.fieldCode().isBlank() || body.fieldName() == null || body.fieldName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段编码和名称不能为空");
        }
        CustomFieldDef def = new CustomFieldDef();
        def.setTypeId(id);
        def.setFieldCode(body.fieldCode().trim());
        def.setFieldName(body.fieldName().trim());
        def.setFieldType(body.fieldType() != null ? FieldType.valueOf(body.fieldType().toUpperCase()) : FieldType.TEXT);
        def.setRequired(Boolean.TRUE.equals(body.required()));
        def.setOptionsJson(body.optionsJson());
        def.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        customFieldDefRepository.save(def);
        return ApiResponse.ok(customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(id).stream()
                .map(ConfigCenterController::toFieldDto).toList());
    }

    // ---------- SLA 策略 ----------

    @GetMapping("/sla-policies")
    public ApiResponse<List<SlaPolicyDto>> slaPolicies() {
        CurrentUser.requirePermission("config:write");
        List<SlaPolicyDto> items = slaPolicyRepository.findAllByOrderByIdAsc().stream()
                .map(p -> SlaPolicyDto.from(p, holidayRepository.findAllByOrderByDateAsc()))
                .toList();
        return ApiResponse.ok(items);
    }

    @PostMapping("/sla-policies")
    @Audit(action = "config.sla.create", module = "config")
    public ApiResponse<SlaPolicy> createSlaPolicy(@RequestBody SlaPolicyBody body) {
        CurrentUser.requirePermission("config:write");
        SlaPolicy policy = new SlaPolicy();
        applyPolicy(policy, body);
        return ApiResponse.ok(slaPolicyRepository.save(policy));
    }

    @PutMapping("/sla-policies/{id}")
    @Audit(action = "config.sla.update", module = "config")
    public ApiResponse<SlaPolicy> updateSlaPolicy(@PathVariable Long id, @RequestBody SlaPolicyBody body) {
        CurrentUser.requirePermission("config:write");
        SlaPolicy policy = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "SLA 策略不存在"));
        applyPolicy(policy, body);
        return ApiResponse.ok(slaPolicyRepository.save(policy));
    }

    @DeleteMapping("/sla-policies/{id}")
    @Audit(action = "config.sla.delete", module = "config")
    public ApiResponse<Void> deleteSlaPolicy(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        slaPolicyRepository.deleteById(id);
        return ApiResponse.ok();
    }

    // ---------- 流程定义 ----------

    @GetMapping("/flows")
    public ApiResponse<FlowDefinition> flows() {
        CurrentUser.requirePermission("config:write");
        FlowDefinition def = new FlowDefinition();
        for (TicketState state : TicketState.values()) {
            FlowDefinition.StateDto s = new FlowDefinition.StateDto();
            s.setCode(state.name());
            s.setName(state.getLabel());
            def.getStates().add(s);
        }
        List<String[]> transitions = List.of(
                new String[]{"CREATED", "CLASSIFIED", "classify", "分类"},
                new String[]{"CREATED", "CANCELED", "cancel", "取消"},
                new String[]{"CLASSIFIED", "PENDING_APPROVAL", "request_approval", "提交审批"},
                new String[]{"CLASSIFIED", "ASSIGNED", "assign", "分配"},
                new String[]{"CLASSIFIED", "ASSIGNED", "auto_assign", "自动分配"},
                new String[]{"CLASSIFIED", "CANCELED", "cancel", "取消"},
                new String[]{"PENDING_APPROVAL", "ASSIGNED", "approve", "审批通过"},
                new String[]{"PENDING_APPROVAL", "CLASSIFIED", "reject", "审批驳回"},
                new String[]{"PENDING_APPROVAL", "CANCELED", "cancel", "取消"},
                new String[]{"ASSIGNED", "IN_PROGRESS", "start", "开始处理"},
                new String[]{"IN_PROGRESS", "RESOLVED", "resolve", "提交解决"},
                new String[]{"RESOLVED", "CLOSED", "confirm", "确认关闭"},
                new String[]{"RESOLVED", "IN_PROGRESS", "reopen", "重新打开"},
                new String[]{"CLOSED", "IN_PROGRESS", "reopen", "重新打开"});
        for (String[] t : transitions) {
            FlowDefinition.TransitionDto dto = new FlowDefinition.TransitionDto();
            dto.setFrom(t[0]);
            dto.setTo(t[1]);
            dto.setAction(t[2]);
            dto.setLabel(t[3]);
            def.getTransitions().add(dto);
        }
        return ApiResponse.ok(def);
    }

    // ---------- 工具 ----------

    private List<TicketTypeDto> toTypeDtos(List<TicketType> types) {
        List<TicketTypeDto> result = new ArrayList<>();
        for (TicketType type : types) {
            TicketTypeDto dto = new TicketTypeDto();
            dto.setId(type.getId());
            dto.setCode(type.getCode());
            dto.setName(type.getName());
            dto.setDescription(type.getDescription());
            dto.setActive(type.isActive());
            dto.setRequiresApproval(type.isRequiresApproval());
            dto.setAutoAssign(type.isAutoAssign());
            dto.setSlaPolicyId(type.getSlaPolicyId());
            dto.setAllowedDeptIds(parseIds(type.getAllowedDeptIds()));
            List<FieldDefDto> fields = customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(type.getId())
                    .stream().map(ConfigCenterController::toFieldDto).toList();
            dto.setFields(fields);
            result.add(dto);
        }
        return result;
    }

    private static FieldDefDto toFieldDto(CustomFieldDef def) {
        FieldDefDto dto = new FieldDefDto();
        dto.setId(def.getId());
        dto.setTicketTypeId(def.getTypeId());
        dto.setFieldCode(def.getFieldCode());
        dto.setFieldName(def.getFieldName());
        dto.setFieldType(def.getFieldType().name());
        dto.setRequired(def.isRequired());
        dto.setOptionsJson(def.getOptionsJson());
        dto.setSortOrder(def.getSortOrder());
        return dto;
    }

    private List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法 id
            }
        }
        return ids;
    }

    private String deptName(Long deptId) {
        return deptId == null ? null : deptRepository.findById(deptId).map(Dept::getName).orElse(null);
    }

    private void applyDept(Dept dept, DeptBody body) {
        if (body.code() == null || body.code().isBlank() || body.name() == null || body.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门编码和名称不能为空");
        }
        dept.setCode(body.code().trim());
        dept.setName(body.name().trim());
        dept.setParentId(body.parentId());
        dept.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
    }

    private void applyType(TicketType type, TicketTypeBody body) {
        if (body.code() == null || body.code().isBlank() || body.name() == null || body.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "类型编码和名称不能为空");
        }
        type.setCode(body.code().trim());
        type.setName(body.name().trim());
        type.setDescription(body.description());
        type.setActive(body.active() == null || body.active());
        type.setRequiresApproval(Boolean.TRUE.equals(body.requiresApproval()));
        type.setAutoAssign(Boolean.TRUE.equals(body.autoAssign()));
        type.setSlaPolicyId(body.slaPolicyId());
        type.setAllowedDeptIds(body.allowedDeptIds() == null || body.allowedDeptIds().isEmpty()
                ? null : body.allowedDeptIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private void applyPolicy(SlaPolicy policy, SlaPolicyBody body) {
        if (body.code() == null || body.code().isBlank() || body.name() == null || body.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "策略编码和名称不能为空");
        }
        policy.setCode(body.code().trim());
        policy.setName(body.name().trim());
        policy.setFirstResponseMinutes(body.firstResponseMinutes() == null ? 120 : body.firstResponseMinutes());
        policy.setResolveMinutes(body.resolveMinutes() == null ? 480 : body.resolveMinutes());
        policy.setBusinessCalendarJson(body.businessCalendarJson());
    }

    // ---------- DTO ----------

    @Data
    public static class RoleDto {
        private String code;
        private String name;
        private String description;
        private List<PermissionDto> permissions;
    }

    @Data
    public static class PermissionDto {
        private String code;
        private String name;
        private String module;
    }

    @Data
    public static class TicketTypeDto {
        private Long id;
        private String code;
        private String name;
        private String description;
        private boolean active;
        private boolean requiresApproval;
        private boolean autoAssign;
        private Long slaPolicyId;
        private List<Long> allowedDeptIds;
        private List<FieldDefDto> fields;
    }

    @Data
    public static class FieldDefDto {
        private Long id;
        private Long ticketTypeId;
        private String fieldCode;
        private String fieldName;
        private String fieldType;
        private boolean required;
        private String optionsJson;
        private int sortOrder;
    }

    @Data
    public static class SlaPolicyDto {
        private Long id;
        private String code;
        private String name;
        private int firstResponseMinutes;
        private int resolveMinutes;
        private String businessCalendarJson;
        private List<HolidayDto> holidays;

        public static SlaPolicyDto from(SlaPolicy policy, List<Holiday> holidays) {
            SlaPolicyDto dto = new SlaPolicyDto();
            dto.setId(policy.getId());
            dto.setCode(policy.getCode());
            dto.setName(policy.getName());
            dto.setFirstResponseMinutes(policy.getFirstResponseMinutes());
            dto.setResolveMinutes(policy.getResolveMinutes());
            dto.setBusinessCalendarJson(policy.getBusinessCalendarJson());
            dto.setHolidays(holidays.stream().map(h -> {
                HolidayDto hd = new HolidayDto();
                hd.setDate(h.getDate());
                hd.setName(h.getName());
                return hd;
            }).toList());
            return dto;
        }
    }

    @Data
    public static class HolidayDto {
        private java.time.LocalDate date;
        private String name;
    }

    @Data
    public static class FlowDefinition {
        private List<StateDto> states = new ArrayList<>();
        private List<TransitionDto> transitions = new ArrayList<>();

        @Data
        public static class StateDto {
            private String code;
            private String name;
        }

        @Data
        public static class TransitionDto {
            private String from;
            private String to;
            private String action;
            private String label;
        }
    }

    public record UserBody(String username, String password, String displayName, String email,
                           Long deptId, Set<String> roles, UserStatus status) {
    }

    public record DeptBody(String code, String name, Long parentId, Integer sortOrder) {
    }

    public record TicketTypeBody(String code, String name, String description, Boolean active,
                                 Boolean requiresApproval, Boolean autoAssign, Long slaPolicyId,
                                 List<Long> allowedDeptIds) {
    }

    public record FieldDefBody(String fieldCode, String fieldName, String fieldType, Boolean required,
                               String optionsJson, Integer sortOrder) {
    }

    public record SlaPolicyBody(String code, String name, Integer firstResponseMinutes,
                                Integer resolveMinutes, String businessCalendarJson) {
    }
}
