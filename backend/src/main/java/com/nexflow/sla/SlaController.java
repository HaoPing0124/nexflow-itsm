package com.nexflow.sla;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sla")
@RequiredArgsConstructor
public class SlaController {

    private final SlaPolicyRepository slaPolicyRepository;

    @GetMapping("/policies")
    public ApiResponse<List<SlaPolicy>> policies() {
        CurrentUser.requirePermission("config:write");
        return ApiResponse.ok(slaPolicyRepository.findAllByOrderByIdAsc());
    }
}
