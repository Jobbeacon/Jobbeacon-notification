package com.jobbeacon.notification.event.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.notification.delivery.DeliveryChannel;
import com.jobbeacon.notification.event.EventEnvelope;
import com.jobbeacon.notification.event.EventHandler;
import com.jobbeacon.notification.event.EventTypes;
import com.jobbeacon.notification.notification.NewNotification;
import com.jobbeacon.notification.notification.NotificationService;
import com.jobbeacon.notification.notification.NotificationType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Welcomes a new user: one in-app notification plus an email. Not subject to a preference, and deduplicated per user,
 * so a resent event never welcomes twice.
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredHandler implements EventHandler {

    static final String TITLE = "Chào mừng bạn đến với JobBeacon";
    /** Keeps the body readable whatever name the backend sends. */
    static final int DISPLAY_NAME_MAX_LENGTH = 100;

    private final ObjectMapper objectMapper;
    private final NotificationService notifications;

    @Override
    public String eventType() {
        return EventTypes.USER_REGISTERED;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        if (envelope.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("USER_REGISTERED without user_id");
        }
        UserRegisteredPayload payload = parse(envelope);
        notifications.create(new NewNotification(
            envelope.userId(),
            NotificationType.WELCOME,
            TITLE,
            body(payload.displayName()),
            Map.of(),
            dedupeKey(envelope.userId()),
            envelope.eventId(),
            Set.of(DeliveryChannel.EMAIL)));
    }

    private UserRegisteredPayload parse(EventEnvelope envelope) {
        if (envelope.payload() == null || !envelope.payload().isObject()) {
            throw new AmqpRejectAndDontRequeueException("USER_REGISTERED payload missing or not an object");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), UserRegisteredPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Invalid user.registered payload", e);
        }
    }

    static String dedupeKey(UUID userId) {
        return "WELCOME:" + userId;
    }

    static String body(String displayName) {
        String greeting = "Xin chào";
        if (StringUtils.hasText(displayName)) {
            String name = displayName.trim();
            greeting += " " + (name.length() > DISPLAY_NAME_MAX_LENGTH ? name.substring(0, DISPLAY_NAME_MAX_LENGTH) : name);
        }
        return greeting + ", tài khoản của bạn đã sẵn sàng. "
            + "Hãy hoàn thiện hồ sơ để nhận gợi ý việc làm phù hợp.";
    }
}
