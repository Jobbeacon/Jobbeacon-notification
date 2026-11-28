package com.jobbeacon.notification.contact;

import java.util.Locale;

/** Mirrors the backend's user status; anything other than ACTIVE receives no email. */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BANNED;

    /**
     * Missing or unknown values (e.g. from a newer backend) are treated as not active rather than failing the
     * event: never email a user whose status we cannot confirm.
     */
    public static UserStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return INACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return INACTIVE;
        }
    }
}
