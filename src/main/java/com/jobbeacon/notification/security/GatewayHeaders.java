package com.jobbeacon.notification.security;

/** Identity headers set by Jobbeacon-gateway after it verified the Firebase token (same contract as the backend). */
public final class GatewayHeaders {

    /** The Firebase uid, not the backend user id. */
    public static final String USER_ID = "X-User-Id";

    private GatewayHeaders() {
    }
}
