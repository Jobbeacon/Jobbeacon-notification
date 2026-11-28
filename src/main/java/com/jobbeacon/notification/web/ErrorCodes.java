package com.jobbeacon.notification.web;

/** i18n message keys used as error codes; each has an entry in messages.properties and messages_vi.properties. */
public final class ErrorCodes {

    public static final String BAD_REQUEST = "bad_request";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String INTERNAL_SERVER_ERROR = "internal_server_error";
    public static final String NOT_FOUND = "not_found";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String USER_NOT_SYNCED = "user_not_synced";
    public static final String NOTIFICATION_NOT_FOUND = "notification_not_found";

    private ErrorCodes() {
    }
}
