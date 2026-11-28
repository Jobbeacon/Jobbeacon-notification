package com.jobbeacon.notification.realtime;

import com.jobbeacon.notification.notification.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes new notifications to the user's open tabs once the creating transaction committed. SSE is only a nudge:
 * a failed push is logged and forgotten, the client refetches the inbox when it reconnects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String NOTIFICATION_CREATED = "notification.created";

    private final SseEmitterRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            registry.send(event.userId(), () -> SseEmitter.event()
                .name(NOTIFICATION_CREATED)
                .id(event.notification().id().toString())
                .data(event.notification()));
        } catch (RuntimeException e) {
            log.warn("SSE push of notification {} failed: {}", event.notification().id(), e.getMessage());
        }
    }

    /** A comment line every ~25s keeps proxies from closing a quiet connection and detects dead ones. */
    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval}")
    public void heartbeat() {
        registry.sendToAll(() -> SseEmitter.event().comment("ping"));
    }
}
