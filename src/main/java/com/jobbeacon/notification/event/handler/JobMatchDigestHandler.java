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
import com.jobbeacon.notification.preference.NotificationPreferenceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Daily "jobs that match you" from recommendation: one in-app notification plus one email. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchDigestHandler implements EventHandler {

    /** Length of {@code notification.dedupe_key}. */
    static final int DEDUPE_KEY_MAX_LENGTH = 200;

    private final ObjectMapper objectMapper;
    private final NotificationService notifications;
    private final NotificationPreferenceService preferences;

    @Override
    public String eventType() {
        return EventTypes.JOB_MATCH_DIGEST;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        if (envelope.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("JOB_MATCH_DIGEST without user_id");
        }
        JobMatchDigestPayload payload = parse(envelope);
        List<JobMatchDigestPayload.Job> jobs = payload.jobs() == null
            ? List.of()
            : payload.jobs().stream().filter(Objects::nonNull).toList();
        if (jobs.isEmpty()) {
            log.debug("Digest {} for user {} has no jobs, skipping", payload.digestKey(), envelope.userId());
            return;
        }
        if (!preferences.isJobMatchEnabled(envelope.userId())) {
            log.debug("User {} turned job match notifications off, skipping", envelope.userId());
            return;
        }
        String digestKey = StringUtils.hasText(payload.digestKey()) ? payload.digestKey() : envelope.eventId().toString();
        notifications.create(new NewNotification(
            envelope.userId(),
            NotificationType.JOB_MATCH_DIGEST,
            "Có %d việc làm phù hợp với bạn".formatted(jobs.size()),
            summary(jobs),
            Map.of("digest_key", digestKey, "jobs", objectMapper.convertValue(jobs, List.class)),
            dedupeKey(digestKey, envelope.userId()),
            envelope.eventId(),
            Set.of(DeliveryChannel.EMAIL)));
    }

    private JobMatchDigestPayload parse(EventEnvelope envelope) {
        if (envelope.payload() == null || !envelope.payload().isObject()) {
            throw new AmqpRejectAndDontRequeueException("JOB_MATCH_DIGEST payload missing or not an object");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), JobMatchDigestPayload.class);
        } catch (JsonProcessingException e) {
            throw new AmqpRejectAndDontRequeueException("Invalid job match digest payload", e);
        }
    }

    /**
     * {@code JOB_MATCH:<digest_key>:<user_id>}; a digest key too long for the column is replaced by its SHA-256 hex,
     * which is still stable, so redeliveries keep deduplicating.
     */
    static String dedupeKey(String digestKey, UUID userId) {
        String key = "JOB_MATCH:%s:%s".formatted(digestKey, userId);
        return key.length() <= DEDUPE_KEY_MAX_LENGTH ? key : "JOB_MATCH:%s:%s".formatted(sha256Hex(digestKey), userId);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** "Title - Company và N việc làm khác"; a missing title or company is left out. */
    static String summary(List<JobMatchDigestPayload.Job> jobs) {
        JobMatchDigestPayload.Job job = jobs.stream().filter(Objects::nonNull).findFirst().orElse(null);
        String first = job == null ? "" : Stream.of(job.title(), job.company())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.joining(" - "));
        if (first.isEmpty()) {
            first = "Việc làm mới";
        }
        return jobs.size() == 1 ? first : first + " và %d việc làm khác".formatted(jobs.size() - 1);
    }
}
