package com.jobbeacon.notification.notification;

import com.jobbeacon.notification.contact.UserContactService;
import com.jobbeacon.notification.security.CurrentUid;
import com.jobbeacon.notification.web.PageResponse;
import com.jobbeacon.notification.web.ResponseGeneral;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's in-app inbox. A user whose contact has not synced yet simply has an empty inbox; writes (mark read)
 * answer 404 {@code user_not_synced}.
 */
@Validated
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final UserContactService contacts;

    @GetMapping
    public ResponseGeneral<PageResponse<NotificationView>> list(
        @CurrentUid String uid,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Optional<UUID> userId = contacts.findUserId(uid);
        PageResponse<NotificationView> result = userId
            .map(id -> PageResponse.of(notifications.list(id, page, size)))
            .orElseGet(() -> new PageResponse<>(List.of(), page, size, 0, 0));
        return ResponseGeneral.ofSuccess("success", result);
    }

    @GetMapping("/unread-count")
    public ResponseGeneral<Map<String, Long>> unreadCount(@CurrentUid String uid) {
        long count = contacts.findUserId(uid).map(notifications::unreadCount).orElse(0L);
        return ResponseGeneral.ofSuccess("success", Map.of("unread_count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseGeneral<NotificationView> markRead(@CurrentUid String uid, @PathVariable UUID id) {
        return ResponseGeneral.ofSuccess("success", notifications.markRead(contacts.requireUserId(uid), id));
    }

    @PatchMapping("/read-all")
    public ResponseGeneral<Map<String, Integer>> markAllRead(@CurrentUid String uid) {
        int updated = notifications.markAllRead(contacts.requireUserId(uid));
        return ResponseGeneral.ofSuccess("success", Map.of("updated", updated));
    }
}
