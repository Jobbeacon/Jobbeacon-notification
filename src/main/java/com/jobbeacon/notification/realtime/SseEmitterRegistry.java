package com.jobbeacon.notification.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open SSE connections per user (one per tab or device), in memory. Enough for one instance; with several, each
 * would also need the fanout described in the README.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void register(UUID userId, SseEmitter emitter) {
        // compute() runs under the map's lock for this key, like remove(), so the add cannot land in a set that
        // remove() has just dropped from the map.
        emitters.compute(userId, (id, set) -> {
            Set<SseEmitter> userEmitters = set != null ? set : new CopyOnWriteArraySet<>();
            userEmitters.add(emitter);
            return userEmitters;
        });
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
    }

    /**
     * Sends to every open connection of the user; a connection that fails is dropped (the client reconnects).
     * {@code event} is called once per connection: an {@link SseEmitter.SseEventBuilder} must not be reused, as
     * every {@code build()} appends to its internal buffer.
     */
    public void send(UUID userId, Supplier<SseEmitter.SseEventBuilder> event) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            sendOrDrop(userId, emitter, event);
        }
    }

    /** Like {@link #send}, for every connection of every user. */
    public void sendToAll(Supplier<SseEmitter.SseEventBuilder> event) {
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> sendOrDrop(userId, emitter, event)));
    }

    public int connectionCount() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }

    private void sendOrDrop(UUID userId, SseEmitter emitter, Supplier<SseEmitter.SseEventBuilder> event) {
        try {
            emitter.send(event.get());
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE connection of user {} is gone: {}", userId, e.getMessage());
            remove(userId, emitter);
            complete(emitter, e);
        }
    }

    /**
     * Ends a connection that failed. The container may already have run its error handling (client gone), and then
     * any further use of the async context throws; the connection is closed either way, so that is ignored.
     */
    private static void complete(SseEmitter emitter, Exception cause) {
        try {
            emitter.completeWithError(cause);
        } catch (IllegalStateException e) {
            log.trace("SSE connection already closed by the container: {}", e.getMessage());
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
