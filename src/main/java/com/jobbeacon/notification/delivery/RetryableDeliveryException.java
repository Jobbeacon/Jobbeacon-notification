package com.jobbeacon.notification.delivery;

/** A send that may succeed later (timeout, provider down, 4xx/5xx temporary): the worker retries with backoff. */
public class RetryableDeliveryException extends RuntimeException {

    public RetryableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
