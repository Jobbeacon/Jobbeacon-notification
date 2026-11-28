package com.jobbeacon.notification.contact;

import java.util.UUID;

/** Payload of {@code user.upserted}: the user's full current state, not only what changed. */
public record UserUpsertedPayload(
    UUID userId,
    String firebaseUid,
    String email,
    String displayName,
    String status
) {
}
