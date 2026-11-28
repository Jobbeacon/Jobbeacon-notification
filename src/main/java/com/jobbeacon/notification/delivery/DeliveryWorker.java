package com.jobbeacon.notification.delivery;

import com.jobbeacon.notification.config.DeliveryProperties;
import com.jobbeacon.notification.contact.UserContact;
import com.jobbeacon.notification.contact.UserContactService;
import com.jobbeacon.notification.notification.Notification;
import com.jobbeacon.notification.notification.NotificationRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends PENDING deliveries. Each poll claims a batch (lease + attempt count in one short transaction), sends outside
 * any transaction, then records the result. A crash mid-send leaves the row PENDING; it is retried once the lease
 * ends. Several instances can poll at once: SKIP LOCKED gives each a different batch. A row whose lease has expired
 * before its turn in the batch is skipped, and results only apply to the attempt that was claimed.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.delivery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryWorker {

    static final String CONTACT_MISSING = "user_contact_missing";
    static final String USER_INACTIVE = "user_inactive";
    static final String NO_DELIVERABLE_EMAIL = "no_deliverable_email";

    private final DeliveryProperties properties;
    private final DeliveryStateService state;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final UserContactService contacts;
    private final Map<DeliveryChannel, ChannelSender> senders = new EnumMap<>(DeliveryChannel.class);

    public DeliveryWorker(DeliveryProperties properties, DeliveryStateService state,
                          NotificationDeliveryRepository deliveries, NotificationRepository notifications,
                          UserContactService contacts, List<ChannelSender> senders) {
        this.properties = properties;
        this.state = state;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.contacts = contacts;
        for (ChannelSender sender : senders) {
            if (this.senders.put(sender.channel(), sender) != null) {
                throw new IllegalStateException("Two senders for channel " + sender.channel());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.delivery.poll-interval}", initialDelayString = "${app.delivery.poll-interval}")
    public void poll() {
        Instant leaseUntil = Instant.now().plus(properties.lease());
        List<ClaimedDelivery> claimed = state.claimDue(properties.batchSize(), leaseUntil);
        for (ClaimedDelivery claim : claimed) {
            try {
                deliver(claim, leaseUntil);
            } catch (RuntimeException e) {
                log.error("Delivery {} failed unexpectedly, it is retried after the lease: {}", claim.id(), e.getMessage(),
                    e);
            }
        }
    }

    private void deliver(ClaimedDelivery claim, Instant leaseUntil) {
        UUID id = claim.id();
        // The claim's attempt number, not the row's: results must apply only to the attempt this poll owns.
        int attempts = claim.attempts();
        Optional<NotificationDelivery> found = deliveries.findById(id);
        if (found.isEmpty() || found.get().getStatus() != DeliveryStatus.PENDING) {
            return;
        }
        NotificationDelivery delivery = found.get();
        Optional<Notification> notification = notifications.findById(delivery.getNotificationId());
        if (notification.isEmpty()) {
            state.markFailed(id, attempts, "notification_missing");
            return;
        }
        Optional<UserContact> contact = contacts.find(notification.get().getUserId());
        if (contact.isEmpty()) {
            // The user.upserted event may not have arrived yet: wait for it like for a temporary failure.
            retryOrFail(id, attempts, CONTACT_MISSING);
            return;
        }
        if (!contact.get().isActive()) {
            state.markFailed(id, attempts, USER_INACTIVE);
            return;
        }
        if (delivery.getChannel() == DeliveryChannel.EMAIL && !contact.get().hasDeliverableEmail()) {
            state.markFailed(id, attempts, NO_DELIVERABLE_EMAIL);
            return;
        }
        ChannelSender sender = senders.get(delivery.getChannel());
        if (sender == null) {
            state.markFailed(id, attempts, "no_sender_for_channel_" + delivery.getChannel());
            return;
        }
        if (Instant.now().isAfter(leaseUntil)) {
            // Another poller may already have reclaimed the row: sending now could send the email twice.
            log.warn("Lease on delivery {} expired before sending; it is left for the next poll", id);
            return;
        }
        try {
            sender.send(notification.get(), contact.get());
            state.markSent(id, attempts);
        } catch (PermanentDeliveryException e) {
            log.warn("Delivery {} failed permanently: {}", id, errorText(e));
            state.markFailed(id, attempts, errorText(e));
        } catch (RuntimeException e) {
            log.warn("Delivery {} attempt {} failed: {}", id, attempts, errorText(e));
            retryOrFail(id, attempts, errorText(e));
        }
    }

    /** {@code attempts} already counts the current attempt (it was incremented when the row was claimed). */
    private void retryOrFail(UUID id, int attempts, String reason) {
        if (attempts >= properties.maxAttempts()) {
            state.markFailed(id, attempts, reason + " (gave up after " + attempts + " attempts)");
        } else {
            state.reschedule(id, attempts, Instant.now().plus(properties.backoffAfter(attempts)), reason);
        }
    }

    /** Some exceptions carry no message; store the class name rather than the string "null". */
    static String errorText(Throwable e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getName();
    }
}
