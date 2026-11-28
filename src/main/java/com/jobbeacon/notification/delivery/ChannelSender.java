package com.jobbeacon.notification.delivery;

import com.jobbeacon.notification.contact.UserContact;
import com.jobbeacon.notification.notification.Notification;

/**
 * Strategy for one {@link DeliveryChannel}. Called outside any transaction. Signals failure only through
 * {@link RetryableDeliveryException} or {@link PermanentDeliveryException}; any other exception counts as retryable.
 */
public interface ChannelSender {

    DeliveryChannel channel();

    void send(Notification notification, UserContact contact);
}
