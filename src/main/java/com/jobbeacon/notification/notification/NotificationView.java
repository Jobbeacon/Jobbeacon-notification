package com.jobbeacon.notification.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A notification as the inbox API and the SSE stream show it. The owner is the caller, so no user id. */
public record NotificationView(
    UUID id,
    NotificationType type,
    String title,
    String body,
    Map<String, Object> payload,
    boolean read,
    Instant readAt,
    Instant createdAt
) {

    public static NotificationView of(Notification notification) {
        return new NotificationView(notification.getId(), notification.getType(),
            notification.getTitle(), notification.getBody(), notification.getPayload(),
            notification.getReadAt() != null, notification.getReadAt(), notification.getCreatedAt());
    }
}
