package com.jobbeacon.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns a RabbitMQ message into an {@link EventEnvelope} and hands it to {@link EventProcessor}. The container acks
 * after this method returns (after the transaction committed); an exception is retried, then dead-lettered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final EventProcessor processor;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void onMessage(Message message) {
        EventEnvelope envelope = parse(message);
        processor.process(envelope);
    }

    private EventEnvelope parse(Message message) {
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("Unreadable event: " + e.getMessage(), e);
        }
        if (envelope.eventId() == null || envelope.eventType() == null) {
            throw new AmqpRejectAndDontRequeueException("Event without event_id or event_type");
        }
        return envelope;
    }
}
