package com.nexflow.approval;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    public ApiResponse<PageResult<ApprovalService.ApprovalDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(approvalService.list(status, CurrentUser.id(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApprovalService.ApprovalDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.detail(id, CurrentUser.id()));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<ApprovalService.ApprovalDto> decision(@PathVariable Long id,
                                                             @RequestBody DecisionBody body) {
        return ApiResponse.ok(approvalService.decide(id, body.approve(), body.comment(), CurrentUser.id()));
    }

    public record DecisionBody(boolean approve, String comment) {
    }
}
