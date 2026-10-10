package com.nexflow.notification;

import com.nexflow.common.CurrentUser;
import com.nexflow.config.SseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseRegistry sseRegistry;

    @GetMapping(value = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = CurrentUser.id();
        SseEmitter emitter = new SseEmitter(300_000L);
        sseRegistry.register(userId, emitter);
        emitter.onCompletion(() -> sseRegistry.remove(userId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            sseRegistry.remove(userId, emitter);
        });
        emitter.onError(e -> sseRegistry.remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ignored) {
            // 客户端未连接时忽略
        }
        return emitter;
    }
}
