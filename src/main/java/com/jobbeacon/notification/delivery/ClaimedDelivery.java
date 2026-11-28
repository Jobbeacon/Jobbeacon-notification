package com.jobbeacon.notification.delivery;

import java.util.UUID;

/** A row claimed by one poll, with its attempt count after the claim; results are recorded against that attempt. */
public record ClaimedDelivery(UUID id, int attempts) {
}
