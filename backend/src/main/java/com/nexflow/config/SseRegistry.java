package com.nexflow.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void register(Long userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
    }

    public void remove(Long userId, SseEmitter emitter) {
        emitters.remove(userId, emitter);
    }

    public void send(Long userId, Map<String, Object> payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                emitter.completeWithError(e);
                emitters.remove(userId);
            }
        }
    }

    public Collection<SseEmitter> all() {
        return emitters.values();
    }
}
