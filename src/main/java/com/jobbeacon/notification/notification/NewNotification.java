package com.jobbeacon.notification.notification;

import com.jobbeacon.notification.delivery.DeliveryChannel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a handler wants to tell a user. {@code dedupeKey} makes the same logical notification (e.g. one digest per
 * user per day) land only once, even if the producer sends it as two different events.
 */
public record NewNotification(
    UUID userId,
    NotificationType type,
    String title,
    String body,
    Map<String, Object> payload,
    String dedupeKey,
    UUID sourceEventId,
    Set<DeliveryChannel> channels
) {
}
