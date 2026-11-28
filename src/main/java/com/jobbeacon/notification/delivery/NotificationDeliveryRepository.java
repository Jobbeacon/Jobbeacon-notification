package com.jobbeacon.notification.delivery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationId(UUID notificationId);

    /** Due rows, locked; rows another poller already holds are skipped instead of waited for. */
    @Query(value = """
        SELECT id, attempts FROM notification_delivery
        WHERE status = 'PENDING' AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<DueRow> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Id and attempt count of a locked due row, before the lease counts the new attempt. */
    interface DueRow {
        UUID getId();

        int getAttempts();
    }

    /** Takes a lease on the claimed rows and counts the attempt, so a crash mid-send retries after the lease. */
    @Modifying
    @Query("""
        UPDATE NotificationDelivery d
        SET d.nextRetryAt = :leaseUntil, d.attempts = d.attempts + 1, d.updatedAt = :now
        WHERE d.id IN :ids
        """)
    int lease(@Param("ids") Collection<UUID> ids, @Param("leaseUntil") Instant leaseUntil, @Param("now") Instant now);

    /*
     * Result updates. Each only applies while the row is still PENDING and still on the attempt that was claimed, so a
     * late result from an expired lease (the row was reclaimed, or already SENT/FAILED) changes nothing.
     */

    @Modifying
    @Query("""
        UPDATE NotificationDelivery d
        SET d.status = :sent, d.sentAt = :now, d.lastError = NULL, d.updatedAt = :now
        WHERE d.id = :id AND d.status = :pending AND d.attempts = :attempts
        """)
    int markSent(@Param("id") UUID id, @Param("attempts") int attempts, @Param("now") Instant now,
                 @Param("pending") DeliveryStatus pending, @Param("sent") DeliveryStatus sent);

    @Modifying
    @Query("""
        UPDATE NotificationDelivery d
        SET d.status = :failed, d.lastError = :reason, d.updatedAt = :now
        WHERE d.id = :id AND d.status = :pending AND d.attempts = :attempts
        """)
    int markFailed(@Param("id") UUID id, @Param("attempts") int attempts, @Param("reason") String reason,
                   @Param("now") Instant now, @Param("pending") DeliveryStatus pending,
                   @Param("failed") DeliveryStatus failed);

    @Modifying
    @Query("""
        UPDATE NotificationDelivery d
        SET d.nextRetryAt = :nextRetryAt, d.lastError = :reason, d.updatedAt = :now
        WHERE d.id = :id AND d.status = :pending AND d.attempts = :attempts
        """)
    int reschedule(@Param("id") UUID id, @Param("attempts") int attempts, @Param("nextRetryAt") Instant nextRetryAt,
                   @Param("reason") String reason, @Param("now") Instant now,
                   @Param("pending") DeliveryStatus pending);
}
