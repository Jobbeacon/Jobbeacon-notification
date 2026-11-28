package com.jobbeacon.notification.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The user's notification switches. No row means the defaults: everything enabled. */
@Entity
@Table(name = "notification_preference")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "job_match_enabled", nullable = false)
    private boolean jobMatchEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
