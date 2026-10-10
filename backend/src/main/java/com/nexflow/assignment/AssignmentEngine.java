package com.nexflow.assignment;

import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.workflow.TicketState;
import com.nexflow.user.User;
import com.nexflow.user.UserRepository;
import com.nexflow.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentEngine {

    private final AssignmentRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final AssignmentRecordRepository recordRepository;
    private final TicketRepository ticketRepository;

    public record Candidate(Long userId, String userName, int workload, long assignedCount, String skillTag) {
    }

    public record AssignmentResult(boolean matched, String ruleName, String ruleStrategy,
                                   List<Candidate> candidates, Long assignedUserId, String reason) {
    }

    public AssignmentResult evaluate(Long typeId, Long deptId, String typeName) {
        List<AssignmentRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        // 规则匹配只看工单类型。
        // scopeDeptId 的语义是"由哪个团队的成员来处理"（决定下面的候选人池），
        // 而不是"工单必须属于该部门"。若把工单部门也作为匹配条件，
        // 人事部提交的 IT 工单将永远匹配不到 IT 支持规则，自动分配形同失效。
        AssignmentRule rule = rules.stream()
                .filter(r -> r.getTicketTypeId() == null || r.getTicketTypeId().equals(typeId))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return new AssignmentResult(false, null, null, List.of(), null, "无匹配规则，待人工分配");
        }
        Long scopeDept = rule.getScopeDeptId() != null ? rule.getScopeDeptId() : deptId;
        List<User> agents = userRepository.findCandidates(scopeDept, rule.getSkillTag(), "AGENT", UserStatus.ACTIVE);
        List<Candidate> pool = agents.stream()
                .map(u -> new Candidate(u.getId(), u.getDisplayName(),
                        (int) ticketRepository.countByAssigneeIdAndStatusIn(u.getId(), List.of(TicketState.ASSIGNED, TicketState.IN_PROGRESS)),
                        recordRepository.countByAssignedUserId(u.getId()),
                        u.getSkillTag()))
                .filter(c -> c.workload() < rule.getWorkloadLimit())
                .toList();
        if (pool.isEmpty()) {
            return new AssignmentResult(true, rule.getName(), rule.getStrategy().getLabel(), List.of(), null,
                    "规则「" + rule.getName() + "」命中，但无符合条件的候选处理人（负载均达上限），待人工分配");
        }
        Candidate selected = switch (rule.getStrategy()) {
            case ROUND_ROBIN -> pool.stream()
                    .min(Comparator.comparingLong(Candidate::assignedCount).thenComparingLong(Candidate::userId))
                    .orElseThrow();
            case LEAST_WORKLOAD -> pool.stream()
                    .min(Comparator.comparingInt(Candidate::workload).thenComparingLong(Candidate::userId))
                    .orElseThrow();
            case MOST_SKILLED -> pool.stream()
                    .min(Comparator.comparingInt((Candidate c) -> matchesSkill(c, rule.getSkillTag()) ? 0 : 1)
                            .thenComparingInt(Candidate::workload)
                            .thenComparingLong(Candidate::userId))
                    .orElseThrow();
        };
        String poolDesc = pool.stream()
                .map(c -> c.userName() + c.workload())
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
        String reason = "规则「" + rule.getName() + "」命中 类型=" + (typeName == null ? String.valueOf(typeId) : typeName)
                + "，策略=" + rule.getStrategy().getLabel() + "；候选" + pool.size() + "人(" + poolDesc + ")，选中 "
                + selected.userName() + "（当前" + selected.workload() + "件最少）";
        return new AssignmentResult(true, rule.getName(), rule.getStrategy().getLabel(), pool, selected.userId(), reason);
    }

    private boolean matchesSkill(Candidate candidate, String skillTag) {
        if (skillTag == null || skillTag.isBlank()) {
            return true;
        }
        return skillTag.equals(candidate.skillTag());
    }

    /**
     * 自动分配：匹配规则并写入 AssignmentRecord，返回被分配人 id（可能为 null 表示待人工分配）。
     */
    public Long assign(Ticket ticket, String typeName) {
        AssignmentResult result = evaluate(ticket.getTypeId(), ticket.getGroupDeptId(), typeName);
        AssignmentRecord record = new AssignmentRecord();
        record.setTicketId(ticket.getId());
        record.setRuleName(result.ruleName());
        record.setMode(AssignmentMode.AUTO);
        record.setAssignedById(0L); // 0 = SYSTEM
        record.setAssignedUserId(result.assignedUserId());
        record.setReason(result.reason());
        record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
        return result.assignedUserId();
    }

    public void recordManual(Ticket ticket, User operator, Long assigneeId, String note) {
        AssignmentRecord record = new AssignmentRecord();
        record.setTicketId(ticket.getId());
        record.setRuleName(null);
        record.setMode(AssignmentMode.MANUAL);
        record.setAssignedById(operator.getId());
        record.setAssignedUserId(assigneeId);
        String operatorName = operator.getDisplayName();
        String assigneeName = assigneeId == null ? "" : userRepository.findById(assigneeId)
                .map(User::getDisplayName).orElse(String.valueOf(assigneeId));
        record.setReason("手动分配：" + assigneeName + "（由 " + operatorName + " 操作）"
                + (note != null && !note.isBlank() ? "，" + note : ""));
        record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
    }
}
