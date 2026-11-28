package com.jobbeacon.notification.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Local copy of the backend user, written only from {@code user.upserted}. */
@Entity
@Table(name = "user_contact")
@Getter
@Setter
@NoArgsConstructor
public class UserContact {

    /** Fake address the backend stores when Firebase gives no email; mail to it can never arrive. */
    public static final String PLACEHOLDER_EMAIL_DOMAIN = "@firebase.local";

    /** Column sizes from {@code 001_user_contact.sql}. */
    public static final int FIREBASE_UID_MAX_LENGTH = 128;
    public static final int EMAIL_MAX_LENGTH = 255;
    public static final int DISPLAY_NAME_MAX_LENGTH = 150;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasDeliverableEmail() {
        return email != null && !email.isBlank() && !email.toLowerCase(Locale.ROOT).endsWith(PLACEHOLDER_EMAIL_DOMAIN);
    }
}
