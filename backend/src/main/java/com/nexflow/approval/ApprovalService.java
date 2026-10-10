package com.nexflow.approval;

import com.nexflow.audit.Audit;
import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import com.nexflow.common.PageResult;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserRepository;
import com.nexflow.user.UserService;
import com.nexflow.workflow.WorkflowEngine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final WorkflowEngine workflowEngine;

    @Transactional(readOnly = true)
    public PageResult<ApprovalDto> list(String status, Long userId, int page, int size) {
        List<ApprovalRequest> all;
        if (status != null && !status.isBlank()) {
            ApprovalStatus s = ApprovalStatus.valueOf(status.toUpperCase());
            all = approvalRepository.findAll().stream().filter(a -> a.getStatus() == s).toList();
        } else {
            all = approvalRepository.findAll();
        }
        User user = userService.loadUser(userId);
        boolean seeAll = user.hasRole("ADMIN") || user.hasRole("SUPERVISOR");
        if (!seeAll) {
            all = all.stream().filter(a -> a.getApproverId().equals(userId)).toList();
        }
        all = all.stream()
                .sorted((a, b) -> b.getRequestedAt().compareTo(a.getRequestedAt()))
                .toList();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<ApprovalDto> items = toDtos(all.subList(from, to));
        return PageResult.of(items, all.size(), page, size);
    }

    @Transactional(readOnly = true)
    public ApprovalDto detail(Long approvalId, Long userId) {
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批不存在"));
        User user = userService.loadUser(userId);
        if (!user.hasRole("ADMIN") && !user.hasRole("SUPERVISOR") && !approval.getApproverId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: approval:decide（无权查看该审批）");
        }
        return toDtos(List.of(approval)).get(0);
    }

    @Transactional
    @Audit(action = "approval.decide", module = "approval")
    public ApprovalDto decide(Long approvalId, boolean approve, String comment, Long operatorId) {
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批不存在"));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该审批已处理，不能重复决策");
        }
        User operator = userService.loadUser(operatorId);
        if (!operator.hasPermission("approval:decide")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: approval:decide");
        }
        if (!operator.getId().equals(approval.getApproverId()) && !operator.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅审批人可处理该审批");
        }
        approval.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setComment(comment);
        approval.setDecidedAt(LocalDateTime.now());
        approvalRepository.save(approval);
        workflowEngine.onApprovalDecided(approval.getTicketId(), approve, operator, comment);
        return toDtos(List.of(approval)).get(0);
    }

    private List<ApprovalDto> toDtos(List<ApprovalRequest> approvals) {
        Map<Long, Ticket> tickets = ticketRepository.findAllById(
                        approvals.stream().map(ApprovalRequest::getTicketId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Ticket::getId, t -> t));
        Map<Long, String> userNames = userRepository.findAllById(
                        approvals.stream().map(ApprovalRequest::getApproverId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        List<ApprovalDto> result = new ArrayList<>();
        for (ApprovalRequest approval : approvals) {
            ApprovalDto dto = new ApprovalDto();
            dto.setId(approval.getId());
            dto.setTicketId(approval.getTicketId());
            Ticket ticket = tickets.get(approval.getTicketId());
            dto.setTicketNo(ticket != null ? ticket.getTicketNo() : null);
            dto.setTicketTitle(ticket != null ? ticket.getTitle() : null);
            dto.setTicketStatus(ticket != null ? ticket.getStatus().name() : null);
            dto.setApproverId(approval.getApproverId());
            dto.setApproverName(userNames.getOrDefault(approval.getApproverId(), "未知用户"));
            dto.setStatus(approval.getStatus().name());
            dto.setComment(approval.getComment());
            dto.setRequestedAt(approval.getRequestedAt());
            dto.setDecidedAt(approval.getDecidedAt());
            result.add(dto);
        }
        return result;
    }

    @Data
    public static class ApprovalDto {
        private Long id;
        private Long ticketId;
        private String ticketNo;
        private String ticketTitle;
        private String ticketStatus;
        private Long approverId;
        private String approverName;
        private String status;
        private String comment;
        private LocalDateTime requestedAt;
        private LocalDateTime decidedAt;
    }
}
