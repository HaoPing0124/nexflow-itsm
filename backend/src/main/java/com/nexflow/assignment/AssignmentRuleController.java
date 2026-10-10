package com.nexflow.assignment;

import com.nexflow.audit.Audit;
import com.nexflow.common.ApiResponse;
import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignment-rules")
@RequiredArgsConstructor
public class AssignmentRuleController {

    private final AssignmentRuleRepository ruleRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final AssignmentEngine assignmentEngine;

    @GetMapping
    public ApiResponse<List<AssignmentRule>> list() {
        return ApiResponse.ok(ruleRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .toList());
    }

    @PostMapping
    @Audit(action = "assignment-rule.create", module = "assignment")
    public ApiResponse<AssignmentRule> create(@RequestBody RuleBody body) {
        CurrentUser.requirePermission("config:write");
        AssignmentRule rule = new AssignmentRule();
        applyBody(rule, body);
        return ApiResponse.ok(ruleRepository.save(rule));
    }

    @PutMapping("/{id}")
    @Audit(action = "assignment-rule.update", module = "assignment")
    public ApiResponse<AssignmentRule> update(@PathVariable Long id, @RequestBody RuleBody body) {
        CurrentUser.requirePermission("config:write");
        AssignmentRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "规则不存在"));
        applyBody(rule, body);
        return ApiResponse.ok(ruleRepository.save(rule));
    }

    @DeleteMapping("/{id}")
    @Audit(action = "assignment-rule.delete", module = "assignment")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        CurrentUser.requirePermission("config:write");
        ruleRepository.deleteById(id);
        return ApiResponse.ok();
    }

    @PostMapping("/preview")
    public ApiResponse<AssignmentEngine.AssignmentResult> preview(@RequestBody PreviewBody body) {
        CurrentUser.requirePermission("ticket:assign");
        if (body.typeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请指定工单类型");
        }
        TicketType type = ticketTypeRepository.findById(body.typeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单类型不存在"));
        return ApiResponse.ok(assignmentEngine.evaluate(body.typeId(), body.deptId(), type.getName()));
    }

    private void applyBody(AssignmentRule rule, RuleBody body) {
        if (body.name() == null || body.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "规则名称不能为空");
        }
        rule.setName(body.name().trim());
        rule.setActive(body.active() == null || body.active());
        rule.setPriority(body.priority() == null ? 100 : body.priority());
        rule.setTicketTypeId(body.ticketTypeId());
        rule.setScopeDeptId(body.scopeDeptId());
        if (body.strategy() != null) {
            rule.setStrategy(AssignmentStrategy.valueOf(body.strategy().toUpperCase()));
        }
        rule.setWorkloadLimit(body.workloadLimit() == null ? 5 : body.workloadLimit());
        rule.setSkillTag(body.skillTag());
    }

    public record RuleBody(String name, Boolean active, Integer priority, Long ticketTypeId,
                           Long scopeDeptId, String strategy, Integer workloadLimit, String skillTag) {
    }

    public record PreviewBody(Long typeId, Long deptId) {
    }
}
