package com.jobbeacon.notification.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Common wrapper of every event on {@code jobbeacon.events}. {@code eventId} is a UUID the producer generates and is
 * the idempotency key; {@code userId} is the backend user id, null for events not about one user.
 */
public record EventEnvelope(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String producer,
    UUID userId,
    JsonNode payload
) {
}
