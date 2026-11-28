package com.jobbeacon.notification.event;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Claim an event id. Returns 1 when this transaction recorded it, 0 when it was already recorded. A concurrent
     * delivery of the same id blocks on the primary key until the first transaction ends, then gets 0 (committed) or
     * 1 (rolled back).
     */
    @Modifying
    @Query(value = """
        INSERT INTO processed_event (event_id, event_type, processed_at)
        VALUES (:eventId, :eventType, :processedAt)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt);
}
