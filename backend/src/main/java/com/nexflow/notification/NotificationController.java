package com.nexflow.notification;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ApiResponse<PageResult<Notification>> list(@RequestParam(required = false) Boolean unread,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = CurrentUser.id();
        List<Notification> all = Boolean.TRUE.equals(unread)
                ? notificationRepository.findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Notification> items = all.subList(from, to);
        return ApiResponse.ok(PageResult.of(items, all.size(), page, size));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Notification> markRead(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!notification.getUserId().equals(CurrentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 只能操作自己的通知");
        }
        notification.setReadFlag(true);
        return ApiResponse.ok(notificationRepository.save(notification));
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        List<Notification> unread = notificationRepository
                .findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(CurrentUser.id());
        unread.forEach(n -> n.setReadFlag(true));
        notificationRepository.saveAll(unread);
        return ApiResponse.ok();
    }
}
