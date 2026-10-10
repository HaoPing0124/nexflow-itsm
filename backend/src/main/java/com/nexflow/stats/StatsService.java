package com.nexflow.stats;

import com.nexflow.approval.ApprovalRequestRepository;
import com.nexflow.approval.ApprovalStatus;
import com.nexflow.assignment.AssignmentRule;
import com.nexflow.assignment.AssignmentRuleRepository;
import com.nexflow.common.PageResult;
import com.nexflow.sla.SlaService;
import com.nexflow.sla.SlaStatus;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketListItemDto;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.ticket.TicketService;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.user.Dept;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserPrincipal;
import com.nexflow.user.UserRepository;
import com.nexflow.workflow.TicketState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final TicketRepository ticketRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final DeptRepository deptRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SlaService slaService;
    private final AssignmentRuleRepository assignmentRuleRepository;
    private final TicketService ticketService;

    @Transactional(readOnly = true)
    public StatsDtos.Overview overview() {
        StatsDtos.Overview dto = new StatsDtos.Overview();
        dto.setTotal(ticketRepository.count());
        long open = ticketRepository.countByStatusNotIn(List.of(TicketState.CLOSED, TicketState.CANCELED));
        dto.setOpen(open);
        dto.setUnassigned(ticketRepository.countByAssigneeIdIsNullAndStatusIn(
                List.of(TicketState.CREATED, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL)));
        dto.setTodayCreated(ticketRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay()));
        dto.setTodayResolved(ticketRepository.countByResolvedAtAfter(LocalDate.now().atStartOfDay()));

        List<Ticket> openTickets = ticketRepository.findByStatusNotIn(List.of(TicketState.CLOSED, TicketState.CANCELED));
        long atRisk = 0;
        long breached = 0;
        for (Ticket ticket : openTickets) {
            SlaStatus status = slaService.slaStatus(ticket);
            if (status == SlaStatus.AT_RISK) {
                atRisk++;
            } else if (status == SlaStatus.BREACHED) {
                breached++;
            }
        }
        dto.setAtRisk(atRisk);
        dto.setBreached(breached);

        List<Ticket> resolved = ticketRepository.findByResolvedAtNotNull();
        if (resolved.isEmpty()) {
            dto.setAvgResolveHours(0);
            dto.setSlaRate(1);
        } else {
            double totalHours = 0;
            long withinSla = 0;
            for (Ticket ticket : resolved) {
                totalHours += Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes() / 60.0;
                if (ticket.getSlaResolveDueAt() == null
                        || !ticket.getResolvedAt().isAfter(ticket.getSlaResolveDueAt())) {
                    withinSla++;
                }
            }
            dto.setAvgResolveHours(Math.round(totalHours / resolved.size() * 10) / 10.0);
            dto.setSlaRate(Math.round(withinSla * 100.0 / resolved.size()) / 100.0);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<StatsDtos.TrendPoint> trend(int days) {
        int safeDays = days <= 0 ? 30 : Math.min(days, 365);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(safeDays - 1L);
        Map<LocalDate, long[]> counts = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) {
            counts.put(from.plusDays(i), new long[2]);
        }
        for (Ticket ticket : ticketRepository.findByCreatedAtAfter(from.atStartOfDay())) {
            LocalDate date = ticket.getCreatedAt().toLocalDate();
            long[] c = counts.get(date);
            if (c != null) {
                c[0]++;
            }
        }
        for (Ticket ticket : ticketRepository.findByResolvedAtAfter(from.atStartOfDay())) {
            LocalDate date = ticket.getResolvedAt().toLocalDate();
            long[] c = counts.get(date);
            if (c != null) {
                c[1]++;
            }
        }
        return counts.entrySet().stream()
                .map(e -> new StatsDtos.TrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<TicketListItemDto> slaRisk(int page, int size) {
        List<Ticket> open = ticketRepository.findByStatusNotIn(List.of(TicketState.CLOSED, TicketState.CANCELED));
        List<Ticket> risk = open.stream()
                .filter(t -> slaService.slaStatus(t) != SlaStatus.OK)
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .toList();
        int from = Math.min((page - 1) * size, risk.size());
        int to = Math.min(from + size, risk.size());
        List<TicketListItemDto> items = ticketService.toListItems(risk.subList(from, to));
        return PageResult.of(items, risk.size(), page, size);
    }

    @Transactional(readOnly = true)
    public List<StatsDtos.DeptPerformance> deptPerformance() {
        Map<Long, Dept> depts = deptRepository.findAll().stream()
                .collect(Collectors.toMap(Dept::getId, d -> d));
        Map<Long, List<Ticket>> byDept = new HashMap<>();
        for (Ticket ticket : ticketRepository.findAll()) {
            if (ticket.getGroupDeptId() == null) {
                continue;
            }
            byDept.computeIfAbsent(ticket.getGroupDeptId(), k -> new ArrayList<>()).add(ticket);
        }
        List<StatsDtos.DeptPerformance> result = new ArrayList<>();
        for (Map.Entry<Long, List<Ticket>> entry : byDept.entrySet()) {
            List<Ticket> tickets = entry.getValue();
            List<Ticket> resolved = tickets.stream()
                    .filter(t -> t.getResolvedAt() != null)
                    .toList();
            StatsDtos.DeptPerformance dto = new StatsDtos.DeptPerformance();
            dto.setDeptId(entry.getKey());
            dto.setDeptName(depts.containsKey(entry.getKey()) ? depts.get(entry.getKey()).getName() : null);
            dto.setTotal(tickets.size());
            dto.setResolved(resolved.size());
            if (resolved.isEmpty()) {
                dto.setAvgHours(0);
                dto.setSlaRate(1);
            } else {
                double totalHours = 0;
                long within = 0;
                for (Ticket ticket : resolved) {
                    totalHours += Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes() / 60.0;
                    if (ticket.getSlaResolveDueAt() == null || !ticket.getResolvedAt().isAfter(ticket.getSlaResolveDueAt())) {
                        within++;
                    }
                }
                dto.setAvgHours(Math.round(totalHours / resolved.size() * 10) / 10.0);
                dto.setSlaRate(Math.round(within * 100.0 / resolved.size()) / 100.0);
            }
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StatsDtos.Workload> workload() {
        Map<Long, List<Ticket>> byAssignee = new HashMap<>();
        for (Ticket ticket : ticketRepository.findAll()) {
            if (ticket.getAssigneeId() == null) {
                continue;
            }
            byAssignee.computeIfAbsent(ticket.getAssigneeId(), k -> new ArrayList<>()).add(ticket);
        }
        int maxLimit = assignmentRuleRepository.findAll().stream()
                .mapToInt(AssignmentRule::getWorkloadLimit)
                .max().orElse(5);
        Map<Long, User> users = userRepository.findAllById(byAssignee.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<StatsDtos.Workload> result = new ArrayList<>();
        for (Map.Entry<Long, List<Ticket>> entry : byAssignee.entrySet()) {
            List<Ticket> tickets = entry.getValue();
            long inProgress = tickets.stream().filter(t -> t.getStatus() == TicketState.IN_PROGRESS).count();
            StatsDtos.Workload dto = new StatsDtos.Workload();
            dto.setUserId(entry.getKey());
            dto.setUserName(users.containsKey(entry.getKey())
                    ? users.get(entry.getKey()).getDisplayName() : "未知用户");
            dto.setTotal(tickets.size());
            dto.setInProgress(inProgress);
            dto.setExceedLimit(inProgress > maxLimit);
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public StatsDtos.Queues queues() {
        StatsDtos.Queues dto = new StatsDtos.Queues();
        dto.setPendingApprovals(approvalRepository.countByStatus(ApprovalStatus.PENDING));
        dto.setUnassigned(ticketRepository.countByAssigneeIdIsNullAndStatusIn(
                List.of(TicketState.CREATED, TicketState.CLASSIFIED, TicketState.PENDING_APPROVAL)));
        return dto;
    }
}
