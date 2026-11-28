package com.jobbeacon.notification.delivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactions around the worker's state changes; the send itself happens between them, unlocked. Result updates
 * take the attempt number seen at claim time and apply only while the row is still PENDING on that attempt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStateService {

    static final int MAX_ERROR_LENGTH = 2000;

    private final NotificationDeliveryRepository repository;

    /**
     * Locks due rows with SKIP LOCKED and leases them, so this poll owns them until the lease ends. Returns each row
     * with the attempt number of this claim.
     */
    @Transactional
    public List<ClaimedDelivery> claimDue(int limit, Instant leaseUntil) {
        Instant now = Instant.now();
        List<NotificationDeliveryRepository.DueRow> rows = repository.lockDue(now, limit);
        if (rows.isEmpty()) {
            return List.of();
        }
        repository.lease(rows.stream().map(NotificationDeliveryRepository.DueRow::getId).toList(), leaseUntil, now);
        // The rows stay locked until commit, so attempts + 1 is exactly what the lease wrote.
        return rows.stream().map(row -> new ClaimedDelivery(row.getId(), row.getAttempts() + 1)).toList();
    }

    @Transactional
    public boolean markSent(UUID id, int attempts) {
        return applied(id, "SENT",
            repository.markSent(id, attempts, Instant.now(), DeliveryStatus.PENDING, DeliveryStatus.SENT));
    }

    @Transactional
    public boolean markFailed(UUID id, int attempts, String reason) {
        return applied(id, "FAILED", repository.markFailed(id, attempts, truncate(reason), Instant.now(),
            DeliveryStatus.PENDING, DeliveryStatus.FAILED));
    }

    @Transactional
    public boolean reschedule(UUID id, int attempts, Instant nextRetryAt, String reason) {
        return applied(id, "reschedule", repository.reschedule(id, attempts, nextRetryAt, truncate(reason),
            Instant.now(), DeliveryStatus.PENDING));
    }

    private static boolean applied(UUID id, String result, int updated) {
        if (updated == 0) {
            log.warn("Delivery {} result {} ignored: the row is no longer PENDING on the claimed attempt", id, result);
        }
        return updated > 0;
    }

    private static String truncate(String reason) {
        return reason == null || reason.length() <= MAX_ERROR_LENGTH ? reason : reason.substring(0, MAX_ERROR_LENGTH);
    }
}
