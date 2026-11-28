package com.jobbeacon.notification.preference;

import com.jobbeacon.notification.contact.UserContactService;
import com.jobbeacon.notification.security.CurrentUid;
import com.jobbeacon.notification.web.ResponseGeneral;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;
    private final UserContactService contacts;

    /** A user not synced yet has no row, so the defaults apply. */
    @GetMapping
    public ResponseGeneral<NotificationPreferenceResponse> get(@CurrentUid String uid) {
        boolean enabled = contacts.findUserId(uid).map(preferences::isJobMatchEnabled).orElse(true);
        return ResponseGeneral.ofSuccess("success", new NotificationPreferenceResponse(enabled));
    }

    @PutMapping
    public ResponseGeneral<NotificationPreferenceResponse> update(
        @CurrentUid String uid, @Valid @RequestBody NotificationPreferenceRequest request) {
        boolean enabled = preferences.setJobMatchEnabled(contacts.requireUserId(uid), request.jobMatchEnabled());
        return ResponseGeneral.ofSuccess("success", new NotificationPreferenceResponse(enabled));
    }
}
