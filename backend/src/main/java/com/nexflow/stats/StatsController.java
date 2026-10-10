package com.nexflow.stats;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.PageResult;
import com.nexflow.ticket.TicketListItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<StatsDtos.Overview> overview() {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.overview());
    }

    @GetMapping("/trend")
    public ApiResponse<List<StatsDtos.TrendPoint>> trend(@RequestParam(defaultValue = "30") int days) {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.trend(days));
    }

    @GetMapping("/sla-risk")
    public ApiResponse<PageResult<TicketListItemDto>> slaRisk(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.slaRisk(page, size));
    }

    @GetMapping("/dept-performance")
    public ApiResponse<List<StatsDtos.DeptPerformance>> deptPerformance() {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.deptPerformance());
    }

    @GetMapping("/workload")
    public ApiResponse<List<StatsDtos.Workload>> workload() {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.workload());
    }

    @GetMapping("/queues")
    public ApiResponse<StatsDtos.Queues> queues() {
        CurrentUser.requirePermission("stats:view");
        return ApiResponse.ok(statsService.queues());
    }
}
