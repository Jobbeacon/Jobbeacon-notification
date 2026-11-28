package com.jobbeacon.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Delivery worker: how often it polls, how many rows per poll, and the retry schedule. */
@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(
    boolean enabled,
    Duration pollInterval,
    int batchSize,
    int maxAttempts,
    Duration lease,
    Duration initialBackoff,
    Duration maxBackoff
) {

    /** Wait before the next attempt: initial backoff doubled per attempt already made, capped at max backoff. */
    public Duration backoffAfter(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = initialBackoff.multipliedBy(1L << exponent);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
