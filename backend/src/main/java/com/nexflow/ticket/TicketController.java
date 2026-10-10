package com.nexflow.ticket;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.PageResult;
import com.nexflow.user.UserPrincipal;
import com.nexflow.workflow.TransitionAction;
import com.nexflow.workflow.TransitionRequest;
import com.nexflow.workflow.WorkflowEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final WorkflowEngine workflowEngine;

    @PostMapping
    public ApiResponse<TicketDetailDto> create(@Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.create(request, CurrentUser.get()));
    }

    @GetMapping
    public ApiResponse<PageResult<TicketListItemDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        TicketQuery query = new TicketQuery(status, typeId, deptId, priority, assigneeId, keyword, view, sort, page, size);
        return ApiResponse.ok(ticketService.list(query, CurrentUser.get()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.detail(id, CurrentUser.get()));
    }

    @PostMapping("/{id}/classify")
    public ApiResponse<TicketDetailDto> classify(@PathVariable Long id, @RequestBody(required = false) NoteBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.CLASSIFY, id, user.getUserId(),
                TransitionRequest.of(body == null ? null : body.note()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<TicketDetailDto> assign(@PathVariable Long id, @RequestBody AssignBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.ASSIGN, id, user.getUserId(),
                new TransitionRequest(body.note(), body.assigneeId(), null));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    /** 路径与动作码保持一致（auto_assign），前端可直接以 legalActions[].action 拼装请求。 */
    @PostMapping("/{id}/auto_assign")
    public ApiResponse<TicketDetailDto> autoAssign(@PathVariable Long id) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.AUTO_ASSIGN, id, user.getUserId(), TransitionRequest.of(null));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<TicketDetailDto> start(@PathVariable Long id, @RequestBody(required = false) NoteBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.START, id, user.getUserId(),
                TransitionRequest.of(body == null ? null : body.note()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<TicketDetailDto> resolve(@PathVariable Long id, @RequestBody(required = false) ResolveBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.RESOLVE, id, user.getUserId(),
                new TransitionRequest(null, null, body == null ? null : body.resolution()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<TicketDetailDto> confirm(@PathVariable Long id, @RequestBody(required = false) NoteBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.CONFIRM, id, user.getUserId(),
                TransitionRequest.of(body == null ? null : body.note()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<TicketDetailDto> reopen(@PathVariable Long id, @RequestBody(required = false) NoteBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.REOPEN, id, user.getUserId(),
                TransitionRequest.of(body == null ? null : body.note()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<TicketDetailDto> cancel(@PathVariable Long id, @RequestBody(required = false) NoteBody body) {
        UserPrincipal user = CurrentUser.get();
        workflowEngine.transition(TransitionAction.CANCEL, id, user.getUserId(),
                TransitionRequest.of(body == null ? null : body.note()));
        return ApiResponse.ok(ticketService.detail(id, user));
    }

    @PostMapping("/batch")
    public ApiResponse<BatchRequest.BatchResult> batch(@Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(ticketService.batch(request, CurrentUser.get()));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<TicketDetailDto.CommentDto>> comments(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.listComments(id, CurrentUser.get()));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<TicketDetailDto.CommentDto> addComment(@PathVariable Long id,
                                                              @RequestBody CommentRequest request) {
        return ApiResponse.ok(ticketService.addComment(id, request, CurrentUser.get()));
    }

    public record NoteBody(String note) {
    }

    public record AssignBody(Long assigneeId, String note) {
    }

    public record ResolveBody(String resolution) {
    }
}
