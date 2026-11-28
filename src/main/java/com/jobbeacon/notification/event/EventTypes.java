package com.jobbeacon.notification.event;

/** {@code event_type} values of the events this service handles. */
public final class EventTypes {

    public static final String USER_UPSERTED = "USER_UPSERTED";
    public static final String JOB_MATCH_DIGEST = "JOB_MATCH_DIGEST";
    public static final String USER_REGISTERED = "USER_REGISTERED";

    private EventTypes() {
    }
}
