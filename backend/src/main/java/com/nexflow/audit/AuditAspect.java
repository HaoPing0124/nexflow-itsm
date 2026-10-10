package com.nexflow.audit;

import com.nexflow.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            AuditLog log = new AuditLog();
            log.setAction(audit.action());
            log.setModule(audit.module());
            log.setEntity(joinPoint.getTarget().getClass().getSimpleName());
            String username = CurrentUser.username();
            log.setUsername(username != null ? username : "system");
            log.setUserId(CurrentUser.id());
            log.setIp(currentIp());
            log.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {
            // 审计失败不影响业务
        }
        return result;
    }

    private String currentIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        // 经反向代理时 remoteAddr 是网关地址，真实客户端在 X-Forwarded-For 的第一段
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = null;
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return normalizeIp(ip);
    }

    /**
     * 纯本机访问时 getRemoteAddr() 返回 IPv6 回环地址 0:0:0:0:0:0:0:1，
     * 写进审计日志后既长又难读。这里统一归一成 IPv4 形态；
     * IPv4-mapped 的 ::ffff:192.168.1.5 也还原成 IPv4。
     */
    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()) {
            return null;
        }
        if ("0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
            return "127.0.0.1";
        }
        if (value.regionMatches(true, 0, "::ffff:", 0, 7)) {
            return value.substring(7);
        }
        return value;
    }
}
