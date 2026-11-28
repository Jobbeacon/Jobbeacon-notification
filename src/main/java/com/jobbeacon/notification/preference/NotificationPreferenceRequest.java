package com.jobbeacon.notification.preference;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(@NotNull(message = "bad_request") Boolean jobMatchEnabled) {
}
