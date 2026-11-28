package com.jobbeacon.notification.event;

/**
 * Strategy for one {@code event_type}. Handlers run inside the processing transaction: they only write to the
 * database (and publish Spring events), never call SMTP or other remote systems.
 */
public interface EventHandler {

    String eventType();

    void handle(EventEnvelope envelope);
}
