package com.jobbeacon.notification.provider;

/**
 * Adapter over an email library or service. Implementations translate their own errors into
 * {@link com.jobbeacon.notification.delivery.RetryableDeliveryException} or
 * {@link com.jobbeacon.notification.delivery.PermanentDeliveryException}; nothing outside this package sees them.
 */
public interface EmailProvider {

    void send(EmailMessage message);
}
