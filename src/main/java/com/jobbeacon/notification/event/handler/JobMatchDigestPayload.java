package com.jobbeacon.notification.event.handler;

import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code recommendation.job-match.digest}. {@code digestKey} identifies the run (e.g. the date), so one
 * user gets one digest per run however many times the event is sent.
 */
public record JobMatchDigestPayload(String digestKey, List<Job> jobs) {

    public record Job(UUID jobId, String title, String company, String url) {
    }
}
