package com.jobbeacon.notification.contact;

import com.jobbeacon.notification.web.ApiException;
import com.jobbeacon.notification.web.ErrorCodes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserContactService {

    private final UserContactRepository repository;

    /**
     * Writes the snapshot from {@code user.upserted}. A payload that can never fit the table (missing ids or email,
     * over-long uid or email) is rejected straight to the DLQ: retrying it would fail the same way.
     */
    @Transactional
    public void upsert(UserUpsertedPayload payload) {
        if (payload.userId() == null || !StringUtils.hasText(payload.firebaseUid())
            || !StringUtils.hasText(payload.email())) {
            throw new AmqpRejectAndDontRequeueException("user.upserted needs user_id, firebase_uid and email");
        }
        String firebaseUid = payload.firebaseUid().trim();
        String email = payload.email().trim();
        if (firebaseUid.length() > UserContact.FIREBASE_UID_MAX_LENGTH) {
            log.warn("user.upserted for user {}: firebase_uid has {} chars (max {}), rejecting",
                payload.userId(), firebaseUid.length(), UserContact.FIREBASE_UID_MAX_LENGTH);
            throw new AmqpRejectAndDontRequeueException("user.upserted firebase_uid too long");
        }
        if (email.length() > UserContact.EMAIL_MAX_LENGTH) {
            // email is NOT NULL and a truncated address would be wrong, so the event cannot be stored.
            log.warn("user.upserted for user {}: email has {} chars (max {}), rejecting",
                payload.userId(), email.length(), UserContact.EMAIL_MAX_LENGTH);
            throw new AmqpRejectAndDontRequeueException("user.upserted email too long");
        }
        repository.upsert(payload.userId(), firebaseUid, email, normalizeDisplayName(payload.displayName()),
            UserStatus.parse(payload.status()).name(), Instant.now());
    }

    /** Trimmed, blank as null, cut to the column size: the name is only used in greetings. */
    static String normalizeDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return null;
        }
        String trimmed = displayName.trim();
        if (trimmed.length() <= UserContact.DISPLAY_NAME_MAX_LENGTH) {
            return trimmed;
        }
        int end = UserContact.DISPLAY_NAME_MAX_LENGTH;
        // Do not split a surrogate pair (emoji etc.) at the cut.
        if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(0, end).trim();
    }

    @Transactional(readOnly = true)
    public Optional<UserContact> find(UUID userId) {
        return repository.findById(userId);
    }

    /** The backend user id of the caller, if their {@code user.upserted} has arrived. */
    @Transactional(readOnly = true)
    public Optional<UUID> findUserId(String firebaseUid) {
        return repository.findByFirebaseUid(firebaseUid).map(UserContact::getUserId);
    }

    /** Like {@link #findUserId} but for endpoints that cannot answer without the user: 404 until the user is synced. */
    @Transactional(readOnly = true)
    public UUID requireUserId(String firebaseUid) {
        return findUserId(firebaseUid).orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_SYNCED));
    }
}
