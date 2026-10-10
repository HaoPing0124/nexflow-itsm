package com.nexflow.notification;

import com.nexflow.config.SseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseRegistry sseRegistry;

    @Transactional
    public void publish(List<Long> userIds, String type, String title, String content, String link) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> distinct = userIds.stream().distinct().toList();
        for (Long userId : distinct) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setLink(link);
            notification.setReadFlag(false);
            notificationRepository.save(notification);
        }
        // SSE 推送
        for (Long userId : distinct) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("title", title);
            payload.put("content", content);
            payload.put("link", link);
            payload.put("createdAt", LocalDateTime.now().toString());
            sseRegistry.send(userId, payload);
        }
    }
}
