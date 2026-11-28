package com.jobbeacon.notification.event;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches an event to the handler of its type. The event id is claimed in processed_event (INSERT ... ON CONFLICT
 * DO NOTHING) before the handler runs, in the same transaction: a redelivered or concurrently delivered event finds
 * the id taken and is skipped; if the handler fails, the claim rolls back with its rows.
 */
@Slf4j
@Service
public class EventProcessor {

    private final Map<String, EventHandler> handlers = new HashMap<>();
    private final ProcessedEventRepository processedEvents;

    public EventProcessor(List<EventHandler> handlers, ProcessedEventRepository processedEvents) {
        for (EventHandler handler : handlers) {
            EventHandler previous = this.handlers.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers for event type " + handler.eventType() + ": "
                    + previous.getClass().getSimpleName() + ", " + handler.getClass().getSimpleName());
            }
        }
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void process(EventEnvelope envelope) {
        EventHandler handler = handlers.get(envelope.eventType());
        if (handler == null) {
            log.warn("No handler for event type {}, dropping event {}", envelope.eventType(), envelope.eventId());
            return;
        }
        int claimed = processedEvents.insertIfAbsent(envelope.eventId(), envelope.eventType(), Instant.now());
        if (claimed == 0) {
            log.info("Event {} ({}) already processed, skipping", envelope.eventId(), envelope.eventType());
            return;
        }
        handler.handle(envelope);
    }
}
