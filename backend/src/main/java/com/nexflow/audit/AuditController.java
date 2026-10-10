package com.nexflow.audit;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.PageResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ApiResponse<PageResult<AuditLogDto>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser.requirePermission("audit:view");
        Specification<AuditLog> spec = null;
        if (module != null && !module.isBlank()) {
            String m = module.trim();
            spec = (root, q, cb) -> cb.equal(root.get("module"), m);
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            spec = spec == null ? (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start)
                    : spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            LocalDateTime end = to.plusDays(1).atStartOfDay();
            spec = spec == null ? (root, q, cb) -> cb.lessThan(root.get("createdAt"), end)
                    : spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        if (userId != null) {
            spec = spec == null ? (root, q, cb) -> cb.equal(root.get("userId"), userId)
                    : spec.and((root, q, cb) -> cb.equal(root.get("userId"), userId));
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageRequest);
        List<AuditLogDto> items = result.getContent().stream().map(AuditLogDto::from).toList();
        return ApiResponse.ok(PageResult.of(items, result.getTotalElements(), page, size));
    }

    @Data
    public static class AuditLogDto {
        private Long id;
        private Long userId;
        private String username;
        private String action;
        private String module;
        private String entity;
        private String ip;
        private LocalDateTime createdAt;

        public static AuditLogDto from(AuditLog log) {
            AuditLogDto dto = new AuditLogDto();
            dto.setId(log.getId());
            dto.setUserId(log.getUserId());
            dto.setUsername(log.getUsername());
            dto.setAction(log.getAction());
            dto.setModule(log.getModule());
            dto.setEntity(log.getEntity());
            dto.setIp(log.getIp());
            dto.setCreatedAt(log.getCreatedAt());
            return dto;
        }
    }
}
