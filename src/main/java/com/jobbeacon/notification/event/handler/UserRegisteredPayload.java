package com.jobbeacon.notification.event.handler;

import java.util.UUID;

/** Payload of {@code user.registered}: sent once by the backend when a user is created (not by the backfill). */
public record UserRegisteredPayload(UUID userId, String displayName) {
}
