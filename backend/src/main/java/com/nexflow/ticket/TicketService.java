package com.nexflow.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexflow.approval.ApprovalRequest;
import com.nexflow.approval.ApprovalRequestRepository;
import com.nexflow.assignment.AssignmentRecord;
import com.nexflow.assignment.AssignmentRecordRepository;
import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import com.nexflow.common.PageResult;
import com.nexflow.notification.NotificationService;
import com.nexflow.sla.SlaService;
import com.nexflow.tickettype.CustomFieldDef;
import com.nexflow.tickettype.CustomFieldDefRepository;
import com.nexflow.tickettype.FieldType;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.user.DataScopeService;
import com.nexflow.user.Dept;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserPrincipal;
import com.nexflow.user.UserRepository;
import com.nexflow.workflow.LegalAction;
import com.nexflow.workflow.TicketState;
import com.nexflow.workflow.TransitionAction;
import com.nexflow.workflow.TransitionRequest;
import com.nexflow.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final DateTimeFormatter TICKET_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Object LOCK = new Object();

    private final TicketRepository ticketRepository;
    private final TicketCustomFieldValueRepository customFieldValueRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CustomFieldDefRepository customFieldDefRepository;
    private final DeptRepository deptRepository;
    private final UserRepository userRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final AssignmentRecordRepository assignmentRecordRepository;
    private final DataScopeService dataScopeService;
    private final SlaService slaService;
    private final WorkflowEngine workflowEngine;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 创建 ----------

    @Transactional
    public TicketDetailDto create(CreateTicketRequest request, UserPrincipal user) {
        TicketType type = ticketTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单类型不存在"));
        if (!type.isActive()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单类型未启用");
        }
        List<CustomFieldDef> fieldDefs = customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(type.getId());
        validateFields(fieldDefs, request.customFields());

        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setTypeId(type.getId());
        ticket.setStatus(TicketState.CREATED);
        ticket.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        ticket.setCreatorId(user.getUserId());
        ticket.setGroupDeptId(user.getDeptId());
        ticket.setDemo(false);
        ticketRepository.save(ticket);

        if (request.customFields() != null) {
            for (CreateTicketRequest.CustomFieldValueRequest cf : request.customFields()) {
                customFieldValueRepository.save(new TicketCustomFieldValue(ticket.getId(), cf.fieldId(), cf.value()));
            }
        }
        if (request.attachmentIds() != null) {
            for (Long attachmentId : request.attachmentIds()) {
                TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
                if (!attachment.getUploadedById().equals(user.getUserId()) && !user.hasRole("ADMIN")) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 只能关联自己上传的附件");
                }
                attachment.setTicketId(ticket.getId());
                attachmentRepository.save(attachment);
            }
        }
        recordHistory(ticket, null, TicketState.CREATED, "create", user.getUserId(), "提交工单");
        return detail(ticket.getId(), user);
    }

    private void validateFields(List<CustomFieldDef> fieldDefs,
                                List<CreateTicketRequest.CustomFieldValueRequest> customFields) {
        Map<Long, String> provided = new LinkedHashMap<>();
        if (customFields != null) {
            for (CreateTicketRequest.CustomFieldValueRequest cf : customFields) {
                provided.put(cf.fieldId(), cf.value());
            }
        }
        for (CustomFieldDef def : fieldDefs) {
            if (def.isRequired()) {
                String value = provided.get(def.getId());
                if (value == null || value.isBlank()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "字段「" + def.getFieldName() + "」为必填项");
                }
            }
        }
        for (CreateTicketRequest.CustomFieldValueRequest cf : customFields == null ? List.<CreateTicketRequest.CustomFieldValueRequest>of() : customFields) {
            CustomFieldDef def = fieldDefs.stream().filter(f -> f.getId().equals(cf.fieldId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未知的自定义字段: " + cf.fieldId()));
            String value = cf.value();
            if (value == null || value.isBlank()) {
                continue;
            }
            switch (def.getFieldType()) {
                case NUMBER -> {
                    try {
                        Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "字段「" + def.getFieldName() + "」必须为数字");
                    }
                }
                case DATE -> {
                    try {
                        LocalDate.parse(value);
                    } catch (DateTimeParseException e) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "字段「" + def.getFieldName() + "」必须为日期(YYYY-MM-DD)");
                    }
                }
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "字段「" + def.getFieldName() + "」必须为 true/false");
                    }
                }
                case SELECT -> validateSelectOption(def, value);
                default -> {
                    // TEXT/USER 不限制
                }
            }
        }
    }

    private void validateSelectOption(CustomFieldDef def, String value) {
        if (def.getOptionsJson() == null || def.getOptionsJson().isBlank()) {
            return;
        }
        try {
            JsonNode array = objectMapper.readTree(def.getOptionsJson());
            for (JsonNode node : array) {
                String option = node.isObject() ? node.get("value").asText() : node.asText();
                if (Objects.equals(option, value)) {
                    return;
                }
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "字段「" + def.getFieldName() + "」的值不在可选范围内");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return;
        }
    }

    // ---------- 列表 ----------

    @Transactional(readOnly = true)
    public PageResult<TicketListItemDto> list(TicketQuery query, UserPrincipal user) {
        Specification<Ticket> spec = buildSpec(query, user);
        if ("at-risk".equalsIgnoreCase(query.view())) {
            return listAtRisk(spec, query, user);
        }
        PageRequest pageRequest = PageRequest.of(Math.max(query.page() - 1, 0), query.size(), sortOf(query.sort()));
        Page<Ticket> page = ticketRepository.findAll(spec, pageRequest);
        List<TicketListItemDto> items = toListItems(page.getContent());
        return PageResult.of(items, page.getTotalElements(), query.page(), query.size());
    }

    private Specification<Ticket> buildSpec(TicketQuery query, UserPrincipal user) {
        Specification<Ticket> spec = dataScopeService.ticketSpecification(user);
        if (query.status() != null && !query.status().isBlank()) {
            TicketState state = TicketState.valueOf(query.status().toUpperCase());
            spec = spec == null ? ticketStatus(state) : spec.and(ticketStatus(state));
        }
        if (query.typeId() != null) {
            spec = spec == null ? (root, q, cb) -> cb.equal(root.get("typeId"), query.typeId())
                    : spec.and((root, q, cb) -> cb.equal(root.get("typeId"), query.typeId()));
        }
        if (query.deptId() != null) {
            spec = spec == null ? (root, q, cb) -> cb.equal(root.get("groupDeptId"), query.deptId())
                    : spec.and((root, q, cb) -> cb.equal(root.get("groupDeptId"), query.deptId()));
        }
        if (query.priority() != null && !query.priority().isBlank()) {
            Priority priority = Priority.valueOf(query.priority().toUpperCase());
            spec = spec == null ? (root, q, cb) -> cb.equal(root.get("priority"), priority)
                    : spec.and((root, q, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (query.assigneeId() != null) {
            spec = spec == null ? (root, q, cb) -> cb.equal(root.get("assigneeId"), query.assigneeId())
                    : spec.and((root, q, cb) -> cb.equal(root.get("assigneeId"), query.assigneeId()));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String kw = "%" + query.keyword().trim() + "%";
            spec = spec == null ? (root, q, cb) -> cb.or(cb.like(root.get("title"), kw), cb.like(root.get("description"), kw))
                    : spec.and((root, q, cb) -> cb.or(cb.like(root.get("title"), kw), cb.like(root.get("description"), kw)));
        }
        String view = query.view();
        if ("my".equalsIgnoreCase(view)) {
            Specification<Ticket> mine = (root, q, cb) -> cb.or(
                    cb.equal(root.get("creatorId"), user.getUserId()),
                    cb.equal(root.get("assigneeId"), user.getUserId()));
            spec = spec == null ? mine : spec.and(mine);
        } else if ("unassigned".equalsIgnoreCase(view)) {
            Specification<Ticket> unassigned = (root, q, cb) -> cb.and(
                    cb.isNull(root.get("assigneeId")),
                    root.get("status").in(List.of(TicketState.CREATED, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL)));
            spec = spec == null ? unassigned : spec.and(unassigned);
        }
        return spec;
    }

    private Specification<Ticket> ticketStatus(TicketState state) {
        return (root, q, cb) -> cb.equal(root.get("status"), state);
    }

    private PageResult<TicketListItemDto> listAtRisk(Specification<Ticket> spec, TicketQuery query, UserPrincipal user) {
        List<Ticket> all = ticketRepository.findAll(spec == null ? Specification.where(null) : spec,
                Sort.by(Sort.Direction.DESC, "id"));
        List<Ticket> risk = all.stream()
                .filter(t -> slaService.slaStatus(t) != com.nexflow.sla.SlaStatus.OK)
                .toList();
        int from = Math.min((query.page() - 1) * query.size(), risk.size());
        int to = Math.min(from + query.size(), risk.size());
        List<TicketListItemDto> items = toListItems(risk.subList(from, to));
        return PageResult.of(items, risk.size(), query.page(), query.size());
    }

    private Sort sortOf(String sort) {
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                field = parts[0];
            }
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1])) {
                direction = Sort.Direction.ASC;
            }
        }
        String dbField = switch (field) {
            case "updatedAt" -> "updatedAt";
            case "priority" -> "priority";
            case "ticketNo" -> "ticketNo";
            default -> "createdAt";
        };
        return Sort.by(direction, dbField);
    }

    // ---------- 详情 ----------

    @Transactional(readOnly = true)
    public TicketDetailDto detail(Long ticketId, UserPrincipal user) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        if (!dataScopeService.canAccess(ticket, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: ticket:view（无权查看该工单）");
        }
        return assembleDetail(ticket, user);
    }

    private TicketDetailDto assembleDetail(Ticket ticket, UserPrincipal user) {
        TicketDetailDto dto = new TicketDetailDto();
        TicketListItemDto base = toListItem(ticket);
        copyBase(base, dto);
        dto.setDescription(ticket.getDescription());
        dto.setVersion(ticket.getVersion());

        // 自定义字段
        Map<Long, CustomFieldDef> defs = customFieldDefRepository.findByTypeIdOrderBySortOrderAsc(ticket.getTypeId())
                .stream().collect(Collectors.toMap(CustomFieldDef::getId, Function.identity()));
        List<TicketDetailDto.CustomFieldDto> customFields = new ArrayList<>();
        for (TicketCustomFieldValue value : customFieldValueRepository.findByTicketId(ticket.getId())) {
            CustomFieldDef def = defs.get(value.getFieldId());
            if (def == null) {
                continue;
            }
            TicketDetailDto.CustomFieldDto cf = new TicketDetailDto.CustomFieldDto();
            cf.setFieldId(def.getId());
            cf.setFieldName(def.getFieldName());
            cf.setFieldType(def.getFieldType().name());
            cf.setValue(value.getValueJson());
            customFields.add(cf);
        }
        dto.setCustomFields(customFields);

        // 附件
        List<TicketDetailDto.AttachmentDto> attachments = attachmentRepository.findByTicketId(ticket.getId())
                .stream().map(a -> {
                    TicketDetailDto.AttachmentDto ad = new TicketDetailDto.AttachmentDto();
                    ad.setId(a.getId());
                    ad.setFileName(a.getFileName());
                    ad.setSize(a.getSize());
                    ad.setContentType(a.getContentType());
                    return ad;
                }).toList();
        dto.setAttachments(attachments);

        // 评论（内部备注过滤）
        Map<Long, String> commentAuthors = userNames(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(TicketComment::getAuthorId).collect(Collectors.toSet()));
        List<TicketDetailDto.CommentDto> comments = new ArrayList<>();
        for (TicketComment comment : commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())) {
            if (comment.isInternal() && !canSeeInternal(ticket, user)) {
                continue;
            }
            TicketDetailDto.CommentDto cd = new TicketDetailDto.CommentDto();
            cd.setId(comment.getId());
            cd.setAuthorId(comment.getAuthorId());
            cd.setAuthorName(commentAuthors.getOrDefault(comment.getAuthorId(), "未知用户"));
            cd.setContent(comment.getContent());
            cd.setInternal(comment.isInternal());
            cd.setCreatedAt(comment.getCreatedAt());
            comments.add(cd);
        }
        dto.setComments(comments);

        // 状态历史
        Map<Long, String> operatorNames = userNames(historyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(TicketStatusHistory::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<TicketDetailDto.StatusHistoryDto> history = historyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(h -> {
                    TicketDetailDto.StatusHistoryDto sh = new TicketDetailDto.StatusHistoryDto();
                    sh.setFromStatus(h.getFromStatus() != null ? h.getFromStatus().name() : null);
                    sh.setToStatus(h.getToStatus().name());
                    sh.setAction(h.getAction());
                    sh.setOperatorName(h.getOperatorId() != null ? operatorNames.getOrDefault(h.getOperatorId(), "系统") : "系统");
                    sh.setNote(h.getNote());
                    sh.setCreatedAt(h.getCreatedAt());
                    return sh;
                }).toList();
        dto.setStatusHistory(history);

        // 审批
        Map<Long, String> approverNames = userNames(approvalRepository.findByTicketIdOrderByIdAsc(ticket.getId())
                .stream().map(ApprovalRequest::getApproverId).collect(Collectors.toSet()));
        List<TicketDetailDto.ApprovalBriefDto> approvals = approvalRepository.findByTicketIdOrderByIdAsc(ticket.getId())
                .stream().map(a -> {
                    TicketDetailDto.ApprovalBriefDto ad = new TicketDetailDto.ApprovalBriefDto();
                    ad.setId(a.getId());
                    ad.setStatus(a.getStatus().name());
                    ad.setApproverName(approverNames.getOrDefault(a.getApproverId(), "未知用户"));
                    ad.setComment(a.getComment());
                    ad.setRequestedAt(a.getRequestedAt());
                    ad.setDecidedAt(a.getDecidedAt());
                    return ad;
                }).toList();
        dto.setApprovals(approvals);

        // 分配轨迹
        Map<Long, String> assignedNames = userNames(assignmentRecordRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(AssignmentRecord::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<TicketDetailDto.AssignmentTraceDto> traces = assignmentRecordRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(r -> {
                    TicketDetailDto.AssignmentTraceDto at = new TicketDetailDto.AssignmentTraceDto();
                    at.setMode(r.getMode().name());
                    at.setRuleName(r.getRuleName());
                    at.setReason(r.getReason());
                    at.setAssignedUserName(r.getAssignedUserId() != null
                            ? assignedNames.getOrDefault(r.getAssignedUserId(), "未知用户") : null);
                    at.setCreatedAt(r.getCreatedAt());
                    return at;
                }).toList();
        dto.setAssignmentTrace(traces);

        // legalActions
        List<TicketDetailDto.LegalActionDto> legalActions = workflowEngine.legalActions(ticket, user)
                .stream().map(la -> {
                    TicketDetailDto.LegalActionDto laDto = new TicketDetailDto.LegalActionDto();
                    laDto.setAction(la.action());
                    laDto.setLabel(la.label());
                    return laDto;
                }).toList();
        dto.setLegalActions(legalActions);
        return dto;
    }

    private boolean canSeeInternal(Ticket ticket, UserPrincipal user) {
        if (user.hasRole("ADMIN") || user.hasRole("SUPERVISOR") || user.hasRole("AGENT")) {
            return true;
        }
        return ticket.getGroupDeptId() != null && ticket.getGroupDeptId().equals(user.getDeptId());
    }

    private void copyBase(TicketListItemDto from, TicketDetailDto to) {
        to.setId(from.getId());
        to.setTicketNo(from.getTicketNo());
        to.setTitle(from.getTitle());
        to.setTypeId(from.getTypeId());
        to.setTypeName(from.getTypeName());
        to.setPriority(from.getPriority());
        to.setStatus(from.getStatus());
        to.setAssigneeId(from.getAssigneeId());
        to.setAssigneeName(from.getAssigneeName());
        to.setDeptId(from.getDeptId());
        to.setDeptName(from.getDeptName());
        to.setCreatorId(from.getCreatorId());
        to.setCreatorName(from.getCreatorName());
        to.setSlaDueAt(from.getSlaDueAt());
        to.setSlaStatus(from.getSlaStatus());
        to.setCreatedAt(from.getCreatedAt());
        to.setUpdatedAt(from.getUpdatedAt());
        to.setDemo(from.isDemo());
    }

    // ---------- 评论 ----------

    @Transactional
    public TicketDetailDto.CommentDto addComment(Long ticketId, CommentRequest request, UserPrincipal user) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        if (!dataScopeService.canAccess(ticket, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: ticket:view（无权查看该工单）");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论内容不能为空");
        }
        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(user.getUserId());
        comment.setContent(request.content().trim());
        comment.setInternal(Boolean.TRUE.equals(request.isInternal()));
        commentRepository.save(comment);

        Long notifyUserId = ticket.getAssigneeId() != null ? ticket.getAssigneeId() : ticket.getCreatorId();
        notificationService.publish(List.of(notifyUserId), "TICKET_COMMENT",
                comment.isInternal() ? "内部备注" : "新评论",
                "工单 " + ticket.getTicketNo() + "：" + (comment.getContent().length() > 50
                        ? comment.getContent().substring(0, 50) + "…" : comment.getContent()),
                "/app/tickets/" + ticket.getId());

        TicketDetailDto.CommentDto dto = new TicketDetailDto.CommentDto();
        dto.setId(comment.getId());
        dto.setAuthorId(user.getUserId());
        dto.setAuthorName(user.getDisplayName());
        dto.setContent(comment.getContent());
        dto.setInternal(comment.isInternal());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<TicketDetailDto.CommentDto> listComments(Long ticketId, UserPrincipal user) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        if (!dataScopeService.canAccess(ticket, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: ticket:view（无权查看该工单）");
        }
        Map<Long, String> names = userNames(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream().map(TicketComment::getAuthorId).collect(Collectors.toSet()));
        List<TicketDetailDto.CommentDto> comments = new ArrayList<>();
        for (TicketComment comment : commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)) {
            if (comment.isInternal() && !canSeeInternal(ticket, user)) {
                continue;
            }
            TicketDetailDto.CommentDto cd = new TicketDetailDto.CommentDto();
            cd.setId(comment.getId());
            cd.setAuthorId(comment.getAuthorId());
            cd.setAuthorName(names.getOrDefault(comment.getAuthorId(), "未知用户"));
            cd.setContent(comment.getContent());
            cd.setInternal(comment.isInternal());
            cd.setCreatedAt(comment.getCreatedAt());
            comments.add(cd);
        }
        return comments;
    }

    // ---------- 批量操作 ----------

    /**
     * 批量流转：逐条调用状态机，允许部分成功并逐条回报失败原因。
     *
     * 注意：这里刻意**不加** @Transactional。
     * workflowEngine.transition 自带事务；若外层再套一个事务，二者会共享同一事务，
     * 某一条失败被 catch 吞掉后事务已被标记 rollback-only，外层提交时会抛
     * "Transaction silently rolled back because it has been marked as rollback-only"，
     * 导致整批 500 且成功项也丢失。去掉外层事务后每条各自成事务，失败互不影响。
     */
    public BatchRequest.BatchResult batch(BatchRequest request, UserPrincipal user) {
        TransitionAction action;
        try {
            action = TransitionAction.fromCode(request.action());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的批量操作: " + request.action());
        }
        List<Long> success = new ArrayList<>();
        List<BatchRequest.FailedItem> failed = new ArrayList<>();
        for (Long id : request.ids()) {
            try {
                workflowEngine.transition(action, id, user.getUserId(), new TransitionRequest(null, request.assigneeId(), null));
                success.add(id);
            } catch (BusinessException e) {
                failed.add(new BatchRequest.FailedItem(id, e.getMessage()));
            }
        }
        return new BatchRequest.BatchResult(success, failed);
    }

    // ---------- 装配 ----------

    public List<TicketListItemDto> toListItems(List<Ticket> tickets) {
        Map<Long, TicketType> types = nameMap(ticketTypeRepository.findAll(), TicketType::getId);
        Map<Long, Dept> depts = nameMap(deptRepository.findAll(), Dept::getId);
        Map<Long, User> users = nameMap(userRepository.findAll(), User::getId);
        return tickets.stream()
                .map(t -> toListItem(t, types, depts, users))
                .toList();
    }

    public TicketListItemDto toListItem(Ticket ticket) {
        Map<Long, TicketType> types = nameMap(ticketTypeRepository.findAll(), TicketType::getId);
        Map<Long, Dept> depts = nameMap(deptRepository.findAll(), Dept::getId);
        Map<Long, User> users = nameMap(userRepository.findAll(), User::getId);
        return toListItem(ticket, types, depts, users);
    }

    private TicketListItemDto toListItem(Ticket ticket, Map<Long, TicketType> types,
                                         Map<Long, Dept> depts, Map<Long, User> users) {
        TicketListItemDto dto = new TicketListItemDto();
        dto.setId(ticket.getId());
        dto.setTicketNo(ticket.getTicketNo());
        dto.setTitle(ticket.getTitle());
        dto.setTypeId(ticket.getTypeId());
        dto.setTypeName(types.containsKey(ticket.getTypeId()) ? types.get(ticket.getTypeId()).getName() : null);
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setAssigneeId(ticket.getAssigneeId());
        dto.setAssigneeName(ticket.getAssigneeId() != null && users.containsKey(ticket.getAssigneeId())
                ? users.get(ticket.getAssigneeId()).getDisplayName() : null);
        dto.setDeptId(ticket.getGroupDeptId());
        dto.setDeptName(ticket.getGroupDeptId() != null && depts.containsKey(ticket.getGroupDeptId())
                ? depts.get(ticket.getGroupDeptId()).getName() : null);
        dto.setCreatorId(ticket.getCreatorId());
        dto.setCreatorName(users.containsKey(ticket.getCreatorId())
                ? users.get(ticket.getCreatorId()).getDisplayName() : null);
        dto.setSlaDueAt(ticket.getSlaResolveDueAt() != null ? ticket.getSlaResolveDueAt() : ticket.getSlaFirstDueAt());
        dto.setSlaStatus(slaService.slaStatus(ticket));
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setDemo(ticket.isDemo());
        return dto;
    }

    private <T> Map<Long, T> nameMap(List<T> list, Function<T, Long> idFn) {
        return list.stream().collect(Collectors.toMap(idFn, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> userNames(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    private void recordHistory(Ticket ticket, TicketState from, TicketState to, String action,
                               Long operatorId, String note) {
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setAction(action);
        history.setOperatorId(operatorId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private String generateTicketNo() {
        synchronized (LOCK) {
            String prefix = "NF-" + LocalDate.now().format(TICKET_NO_DATE);
            int seq = ticketRepository.findTopByTicketNoStartingWithOrderByTicketNoDesc(prefix)
                    .map(t -> Integer.parseInt(t.getTicketNo().substring(t.getTicketNo().length() - 4)) + 1)
                    .orElse(1);
            return prefix + "-" + String.format("%04d", seq);
        }
    }

    // 供 stats / search 使用
    public TicketDetailDto.LegalActionDto toLegalActionDto(LegalAction la) {
        TicketDetailDto.LegalActionDto dto = new TicketDetailDto.LegalActionDto();
        dto.setAction(la.action());
        dto.setLabel(la.label());
        return dto;
    }
}
