package com.nexflow.notification;

import com.nexflow.config.SseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class SseHeartbeat {

    private final SseRegistry sseRegistry;

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (SseEmitter emitter : sseRegistry.all()) {
            try {
                emitter.send(SseEmitter.event().comment("-- keepalive --"));
            } catch (Exception ignored) {
                // 连接断开由 SseController 的完成回调清理
            }
        }
    }
}
