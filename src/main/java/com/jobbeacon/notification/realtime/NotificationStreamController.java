package com.jobbeacon.notification.realtime;

import com.jobbeacon.notification.config.RealtimeProperties;
import com.jobbeacon.notification.contact.UserContactService;
import com.jobbeacon.notification.security.CurrentUid;
import com.jobbeacon.notification.web.ApiException;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/v1/me/notifications/stream}: an SSE stream of {@code notification.created} events. The gateway
 * route for this path needs {@code response-timeout: -1}, or the stream is cut after the global timeout.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    static final String ERROR_CODE_HEADER = "X-Error-Code";

    private final SseEmitterRegistry registry;
    private final UserContactService contacts;
    private final RealtimeProperties properties;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUid String uid) throws IOException {
        UUID userId = contacts.requireUserId(uid);
        SseEmitter emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        registry.register(userId, emitter);
        // Flushes the response headers now, so the client knows the stream is open before the first notification.
        emitter.send(SseEmitter.event().comment("connected"));
        return emitter;
    }

    /**
     * The client asked for {@code text/event-stream}, so the JSON error body of the global advice cannot be
     * negotiated and would turn a 401/404 into a 500. Answer with the status only; the code goes in a header.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Void> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).header(ERROR_CODE_HEADER, ex.getCode()).build();
    }
}
