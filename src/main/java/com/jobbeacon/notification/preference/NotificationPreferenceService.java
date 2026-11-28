package com.jobbeacon.notification.preference;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the user's switches (D-006); a user without a row gets the defaults. */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    @Transactional(readOnly = true)
    public boolean isJobMatchEnabled(UUID userId) {
        return repository.findById(userId).map(NotificationPreference::isJobMatchEnabled).orElse(true);
    }

    @Transactional
    public boolean setJobMatchEnabled(UUID userId, boolean enabled) {
        NotificationPreference preference = repository.findById(userId).orElseGet(() -> {
            NotificationPreference created = new NotificationPreference();
            created.setUserId(userId);
            return created;
        });
        preference.setJobMatchEnabled(enabled);
        preference.setUpdatedAt(Instant.now());
        return repository.save(preference).isJobMatchEnabled();
    }
}
