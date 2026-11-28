package com.jobbeacon.notification.event.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.notification.contact.UserContactService;
import com.jobbeacon.notification.contact.UserUpsertedPayload;
import com.jobbeacon.notification.event.EventEnvelope;
import com.jobbeacon.notification.event.EventHandler;
import com.jobbeacon.notification.event.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

/** Keeps {@code user_contact} in step with the backend. No version check: last write wins (D-007). */
@Component
@RequiredArgsConstructor
public class UserUpsertedHandler implements EventHandler {

    private final ObjectMapper objectMapper;
    private final UserContactService contacts;

    @Override
    public String eventType() {
        return EventTypes.USER_UPSERTED;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        contacts.upsert(parse(envelope));
    }

    /** A payload that cannot be read will not get better on retry: reject it straight to the DLQ. */
    private UserUpsertedPayload parse(EventEnvelope envelope) {
        UserUpsertedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), UserUpsertedPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException(
                "Invalid user.upserted payload in event " + envelope.eventId() + " ("
                    + e.getClass().getSimpleName() + ")", e);
        }
        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException(
                "user.upserted event " + envelope.eventId() + " has no payload");
        }
        return payload;
    }
}
