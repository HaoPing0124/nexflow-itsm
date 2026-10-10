package com.nexflow.workflow;

import com.nexflow.approval.ApprovalRequest;
import com.nexflow.approval.ApprovalRequestRepository;
import com.nexflow.approval.ApprovalStatus;
import com.nexflow.assignment.AssignmentEngine;
import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import com.nexflow.notification.NotificationService;
import com.nexflow.sla.HolidayRepository;
import com.nexflow.sla.SlaCalculator;
import com.nexflow.sla.SlaPolicy;
import com.nexflow.sla.SlaService;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.ticket.TicketStatusHistory;
import com.nexflow.ticket.TicketStatusHistoryRepository;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.user.DataScopeService;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserPrincipal;
import com.nexflow.user.UserRepository;
import com.nexflow.user.UserService;
import com.nexflow.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final TicketRepository ticketRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final DeptRepository deptRepository;
    private final DataScopeService dataScopeService;
    private final TicketTypeRepository ticketTypeRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final AssignmentEngine assignmentEngine;
    private final NotificationService notificationService;
    private final SlaService slaService;
    private final SlaCalculator slaCalculator;
    private final HolidayRepository holidayRepository;

    /**
     * 状态流转唯一入口：角色权限 → 数据域 → 前置条件（审批绑定）→ 状态变更 → 历史 + 审计 + 钩子。
     */
    @Transactional
    public Ticket transition(TransitionAction action, Long ticketId, Long operatorId, TransitionRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        User operator = userService.loadUser(operatorId);
        UserPrincipal principal = new UserPrincipal(operator);

        String permission = permissionFor(action);
        if (!principal.hasPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: " + permission);
        }
        if (!dataScopeService.canAccess(ticket, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 无权操作该工单");
        }

        switch (action) {
            case CLASSIFY -> doClassify(ticket, principal, request);
            case ASSIGN -> doAssign(ticket, principal, request, false);
            case AUTO_ASSIGN -> doAssign(ticket, principal, request, true);
            case START -> doStart(ticket, principal, request);
            case RESOLVE -> doResolve(ticket, principal, request);
            case CONFIRM -> doConfirm(ticket, principal, request);
            case REOPEN -> doReopen(ticket, principal, request);
            case CANCEL -> doCancel(ticket, principal, request);
        }
        return ticketRepository.save(ticket);
    }

    private void doClassify(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.CREATED), "分类");
        TicketState from = ticket.getStatus();
        TicketType type = ticketTypeRepository.findById(ticket.getTypeId()).orElse(null);
        ticket.setStatus(TicketState.CLASSIFIED);
        recordHistory(ticket, from, TicketState.CLASSIFIED, "classify", operator.getUserId(),
                request.note() != null ? request.note() : "已完成分类");

        // SLA 起算
        SlaPolicy policy = slaService.policyFor(ticket);
        if (policy != null) {
            Set<LocalDate> holidays = holidayRepository.findAll().stream()
                    .map(h -> h.getDate()).collect(Collectors.toSet());
            LocalDateTime now = LocalDateTime.now();
            ticket.setSlaFirstDueAt(slaCalculator.dueAt(now, policy.getFirstResponseMinutes(), policy, holidays));
            ticket.setSlaResolveDueAt(slaCalculator.dueAt(now, policy.getResolveMinutes(), policy, holidays));
        }

        if (type != null && type.isRequiresApproval()) {
            User approver = findApprover(ticket);
            ApprovalRequest approval = new ApprovalRequest();
            approval.setTicketId(ticket.getId());
            approval.setApproverId(approver.getId());
            approval.setApproverRole("SUPERVISOR");
            approval.setStatus(ApprovalStatus.PENDING);
            approvalRepository.save(approval);
            ticket.setStatus(TicketState.PENDING_APPROVAL);
            recordHistory(ticket, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL, "request_approval",
                    operator.getUserId(), "该类型工单需审批，已提交审批");
            notificationService.publish(List.of(approver.getId()), "APPROVAL_PENDING", "待审批",
                    "工单 " + ticket.getTicketNo() + " 等待您的审批", "/app/approvals");
        } else if (type != null && type.isAutoAssign()) {
            autoAssignInternal(ticket, operator, "分类后自动分配");
        } else {
            notificationService.publish(List.of(ticket.getCreatorId()), "TICKET_UPDATE", "工单已分类",
                    "工单 " + ticket.getTicketNo() + " 已完成分类，等待分配", "/app/tickets/" + ticket.getId());
        }
    }

    private void doAssign(Ticket ticket, UserPrincipal operator, TransitionRequest request, boolean auto) {
        requireStatus(ticket, List.of(TicketState.CLASSIFIED), auto ? "自动分配" : "分配");
        checkNoPendingApproval(ticket, auto ? "自动分配" : "分配");
        TicketType type = ticketTypeRepository.findById(ticket.getTypeId()).orElse(null);
        String typeName = type != null ? type.getName() : null;

        Long assigneeId;
        if (auto) {
            assigneeId = assignmentEngine.assign(ticket, typeName);
        } else {
            if (request.assigneeId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请指定处理人");
            }
            assigneeId = request.assigneeId();
            User operatorUser = userService.loadUser(operator.getUserId());
            assignmentEngine.recordManual(ticket, operatorUser, assigneeId, request.note());
        }

        if (assigneeId == null) {
            // 无匹配规则或候选 → 保持 CLASSIFIED，等待人工分配
            notificationService.publish(List.of(ticket.getCreatorId()), "TICKET_UPDATE", "待人工分配",
                    "工单 " + ticket.getTicketNo() + " 未匹配到自动分配规则，等待人工分配", "/app/tickets/" + ticket.getId());
            return;
        }
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.ASSIGNED);
        ticket.setAssigneeId(assigneeId);
        recordHistory(ticket, from, TicketState.ASSIGNED, auto ? "auto_assign" : "assign",
                operator.getUserId(), request.note());
        notificationService.publish(List.of(assigneeId), "TICKET_ASSIGN", "新工单分配",
                "工单 " + ticket.getTicketNo() + " 已分配给您，请及时处理", "/app/tickets/" + ticket.getId());
    }

    private void autoAssignInternal(Ticket ticket, UserPrincipal operator, String note) {
        TicketType type = ticketTypeRepository.findById(ticket.getTypeId()).orElse(null);
        String typeName = type != null ? type.getName() : null;
        Long assigneeId = assignmentEngine.assign(ticket, typeName);
        if (assigneeId == null) {
            return;
        }
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.ASSIGNED);
        ticket.setAssigneeId(assigneeId);
        recordHistory(ticket, from, TicketState.ASSIGNED, "auto_assign", operator.getUserId(), note);
        notificationService.publish(List.of(assigneeId), "TICKET_ASSIGN", "新工单分配",
                "工单 " + ticket.getTicketNo() + " 已自动分配给您，请及时处理", "/app/tickets/" + ticket.getId());
    }

    private void doStart(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.ASSIGNED), "开始处理");
        checkNoPendingApproval(ticket, "开始处理");
        requireAssigneeOrSuperior(ticket, operator);
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.IN_PROGRESS);
        recordHistory(ticket, from, TicketState.IN_PROGRESS, "start", operator.getUserId(), request.note());
        notificationService.publish(List.of(ticket.getCreatorId()), "TICKET_UPDATE", "开始处理",
                "工单 " + ticket.getTicketNo() + " 已开始处理", "/app/tickets/" + ticket.getId());
    }

    private void doResolve(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.IN_PROGRESS), "提交解决");
        requireAssigneeOrSuperior(ticket, operator);
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.RESOLVED);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setResolution(request.resolution());
        recordHistory(ticket, from, TicketState.RESOLVED, "resolve", operator.getUserId(),
                request.resolution() != null ? request.resolution() : "已提交解决方案");
        notificationService.publish(List.of(ticket.getCreatorId()), "TICKET_UPDATE", "待确认关闭",
                "工单 " + ticket.getTicketNo() + " 已解决，请确认关闭", "/app/tickets/" + ticket.getId());
    }

    private void doConfirm(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.RESOLVED), "确认关闭");
        requireCreatorOrSuperior(ticket, operator);
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());
        recordHistory(ticket, from, TicketState.CLOSED, "confirm", operator.getUserId(), request.note());
        if (ticket.getAssigneeId() != null) {
            notificationService.publish(List.of(ticket.getAssigneeId()), "TICKET_UPDATE", "工单已关闭",
                    "工单 " + ticket.getTicketNo() + " 已确认关闭", "/app/tickets/" + ticket.getId());
        }
    }

    private void doReopen(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.RESOLVED, TicketState.CLOSED), "重新打开");
        boolean isCreator = ticket.getCreatorId().equals(operator.getUserId());
        boolean isAssignee = ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(operator.getUserId());
        if (!isCreator && !isAssignee && !isSuperior(ticket, operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅创建人/处理人/主管可重新打开工单");
        }
        // SLA 恢复：将暂停时长（resolvedAt → 现在）顺延到期时间
        if (ticket.getSlaResolveDueAt() != null && ticket.getResolvedAt() != null) {
            Duration pause = Duration.between(ticket.getResolvedAt(), LocalDateTime.now());
            ticket.setSlaResolveDueAt(ticket.getSlaResolveDueAt().plus(pause));
        }
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.IN_PROGRESS);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        recordHistory(ticket, from, TicketState.IN_PROGRESS, "reopen", operator.getUserId(), request.note());
        if (ticket.getAssigneeId() != null) {
            notificationService.publish(List.of(ticket.getAssigneeId()), "TICKET_UPDATE", "工单重新打开",
                    "工单 " + ticket.getTicketNo() + " 已被重新打开，请继续处理", "/app/tickets/" + ticket.getId());
        }
    }

    private void doCancel(Ticket ticket, UserPrincipal operator, TransitionRequest request) {
        requireStatus(ticket, List.of(TicketState.CREATED, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL), "取消");
        if (!ticket.getCreatorId().equals(operator.getUserId()) && !isSuperior(ticket, operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅创建人/主管可取消工单");
        }
        TicketState from = ticket.getStatus();
        ticket.setStatus(TicketState.CANCELED);
        recordHistory(ticket, from, TicketState.CANCELED, "cancel", operator.getUserId(), request.note());
        if (ticket.getAssigneeId() != null) {
            notificationService.publish(List.of(ticket.getAssigneeId()), "TICKET_UPDATE", "工单已取消",
                    "工单 " + ticket.getTicketNo() + " 已取消", "/app/tickets/" + ticket.getId());
        }
    }

    /**
     * 审批决策后的流转延续：APPROVED → 续流转（自动分配或待分配）；REJECTED → 退回已分类。
     */
    @Transactional
    public void onApprovalDecided(Long ticketId, boolean approved, User operator, String comment) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        TicketType type = ticketTypeRepository.findById(ticket.getTypeId()).orElse(null);
        String typeName = type != null ? type.getName() : null;
        UserPrincipal principal = new UserPrincipal(operator);

        if (approved) {
            if (type != null && type.isAutoAssign()) {
                Long assigneeId = assignmentEngine.assign(ticket, typeName);
                ticket.setStatus(TicketState.ASSIGNED);
                ticket.setAssigneeId(assigneeId);
                recordHistory(ticket, TicketState.PENDING_APPROVAL, TicketState.ASSIGNED, "approve",
                        operator.getId(), "审批通过" + (comment != null ? "：" + comment : "") + "，自动分配");
                if (assigneeId != null) {
                    notificationService.publish(List.of(assigneeId), "TICKET_ASSIGN", "审批通过，新工单分配",
                            "工单 " + ticket.getTicketNo() + " 审批通过并已分配给您", "/app/tickets/" + ticket.getId());
                }
            } else {
                ticket.setStatus(TicketState.CLASSIFIED);
                recordHistory(ticket, TicketState.PENDING_APPROVAL, TicketState.CLASSIFIED, "approve",
                        operator.getId(), "审批通过，等待分配");
                notificationService.publish(List.of(ticket.getCreatorId()), "APPROVAL_RESULT", "审批通过",
                        "工单 " + ticket.getTicketNo() + " 审批通过，等待分配", "/app/tickets/" + ticket.getId());
            }
        } else {
            ticket.setStatus(TicketState.CLASSIFIED);
            recordHistory(ticket, TicketState.PENDING_APPROVAL, TicketState.CLASSIFIED, "reject",
                    operator.getId(), comment != null ? comment : "审批未通过");
            notificationService.publish(List.of(ticket.getCreatorId()), "APPROVAL_RESULT", "审批未通过",
                    "工单 " + ticket.getTicketNo() + " 审批未通过，已退回分类" + (comment != null ? "：" + comment : ""),
                    "/app/tickets/" + ticket.getId());
        }
        ticketRepository.save(ticket);
    }

    /**
     * 按当前状态 + 当前用户角色/数据域计算可执行操作（前端按钮动态生成）。
     */
    public List<LegalAction> legalActions(Ticket ticket, UserPrincipal user) {
        List<LegalAction> actions = new ArrayList<>();
        TicketState status = ticket.getStatus();
        boolean canAccess = dataScopeService.canAccess(ticket, user);
        if (!canAccess) {
            return actions;
        }
        if (status == TicketState.CREATED && user.hasPermission("ticket:classify")) {
            actions.add(new LegalAction("classify", "分类"));
        }
        if (status == TicketState.CLASSIFIED && user.hasPermission("ticket:assign")) {
            actions.add(new LegalAction("assign", "分配"));
            actions.add(new LegalAction("auto_assign", "自动分配"));
        }
        if (status == TicketState.ASSIGNED && user.hasPermission("ticket:resolve")
                && (isAssignee(ticket, user) || isSuperior(ticket, user))) {
            actions.add(new LegalAction("start", "开始处理"));
        }
        if (status == TicketState.IN_PROGRESS && user.hasPermission("ticket:resolve")
                && (isAssignee(ticket, user) || isSuperior(ticket, user))) {
            actions.add(new LegalAction("resolve", "提交解决"));
        }
        if (status == TicketState.RESOLVED && user.hasPermission("ticket:close")
                && (ticket.getCreatorId().equals(user.getUserId()) || isSuperior(ticket, user))) {
            actions.add(new LegalAction("confirm", "确认关闭"));
            actions.add(new LegalAction("reopen", "重新打开"));
        }
        if (status == TicketState.CLOSED && user.hasPermission("ticket:close")
                && (ticket.getCreatorId().equals(user.getUserId()) || isSuperior(ticket, user))) {
            actions.add(new LegalAction("reopen", "重新打开"));
        }
        if ((status == TicketState.CREATED || status == TicketState.CLASSIFIED
                || status == TicketState.PENDING_APPROVAL) && user.hasPermission("ticket:close")
                && (ticket.getCreatorId().equals(user.getUserId()) || isSuperior(ticket, user))) {
            actions.add(new LegalAction("cancel", "取消工单"));
        }
        return actions;
    }

    private String permissionFor(TransitionAction action) {
        return switch (action) {
            case CLASSIFY -> "ticket:classify";
            case ASSIGN, AUTO_ASSIGN -> "ticket:assign";
            case START, RESOLVE -> "ticket:resolve";
            case CONFIRM, REOPEN, CANCEL -> "ticket:close";
        };
    }

    private void requireStatus(Ticket ticket, List<TicketState> allowed, String actionName) {
        if (!allowed.contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_TRANSITION,
                    "非法流转：工单当前状态为「" + ticket.getStatus().getLabel() + "」，不能执行「" + actionName + "」");
        }
    }

    private void checkNoPendingApproval(Ticket ticket, String actionName) {
        long pending = approvalRepository.countByTicketIdAndStatus(ticket.getId(), ApprovalStatus.PENDING);
        if (pending > 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_TRANSITION,
                    "工单存在待审批事项，审批完成前不能执行「" + actionName + "」");
        }
    }

    private void requireAssigneeOrSuperior(Ticket ticket, UserPrincipal operator) {
        if (!isAssignee(ticket, operator) && !isSuperior(ticket, operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅处理人或主管/管理员可执行该操作");
        }
    }

    private void requireCreatorOrSuperior(Ticket ticket, UserPrincipal operator) {
        if (!ticket.getCreatorId().equals(operator.getUserId()) && !isSuperior(ticket, operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅创建人或主管/管理员可执行该操作");
        }
    }

    private boolean isAssignee(Ticket ticket, UserPrincipal operator) {
        return ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(operator.getUserId());
    }

    private boolean isSuperior(Ticket ticket, UserPrincipal operator) {
        if (operator.hasRole("ADMIN")) {
            return true;
        }
        return operator.hasRole("SUPERVISOR") && dataScopeService.canAccess(ticket, operator);
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

    private User findApprover(Ticket ticket) {
        Long deptId = ticket.getGroupDeptId();
        List<User> supervisors = userRepository.findCandidates(deptId, null, "SUPERVISOR", UserStatus.ACTIVE);
        if (!supervisors.isEmpty()) {
            return supervisors.get(0);
        }
        List<User> anySupervisor = userRepository.findCandidates(null, null, "SUPERVISOR", UserStatus.ACTIVE);
        if (!anySupervisor.isEmpty()) {
            return anySupervisor.get(0);
        }
        List<User> admins = userRepository.findCandidates(null, null, "ADMIN", UserStatus.ACTIVE);
        if (!admins.isEmpty()) {
            return admins.get(0);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到可审批的主管，请先配置主管角色用户");
    }
}
