package com.jobbeacon.notification.notification;

import java.util.UUID;

/**
 * Published inside the creating transaction; listeners that reach outside (SSE) run after commit. {@code userId}
 * (the backend user id) says whose streams get the push; the view itself does not expose it to the client.
 */
public record NotificationCreatedEvent(UUID userId, NotificationView notification) {
}
