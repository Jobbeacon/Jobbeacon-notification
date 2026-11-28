package com.jobbeacon.notification.web;

import java.time.LocalDate;

/** Same envelope as the backend and recommendation, so the frontend handles every service's responses alike. */
public record ResponseGeneral<T>(int status, String message, T data, String timestamp) {

    public static <T> ResponseGeneral<T> of(int status, String message, T data) {
        return new ResponseGeneral<>(status, message, data, LocalDate.now().toString());
    }

    public static <T> ResponseGeneral<T> ofSuccess(String message, T data) {
        return of(200, message, data);
    }

    public static <T> ResponseGeneral<T> ofSuccess(String message) {
        return of(200, message, null);
    }
}
