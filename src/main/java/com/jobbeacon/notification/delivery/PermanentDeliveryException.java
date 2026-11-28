package com.jobbeacon.notification.delivery;

/** A send that will never succeed (invalid address, unrenderable message): the delivery is marked FAILED at once. */
public class PermanentDeliveryException extends RuntimeException {

    public PermanentDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
