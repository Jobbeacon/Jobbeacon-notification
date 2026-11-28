package com.jobbeacon.notification.notification;

import com.jobbeacon.notification.delivery.DeliveryChannel;
import com.jobbeacon.notification.delivery.NotificationDelivery;
import com.jobbeacon.notification.delivery.NotificationDeliveryRepository;
import com.jobbeacon.notification.web.ApiException;
import com.jobbeacon.notification.web.ErrorCodes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final ApplicationEventPublisher events;

    /**
     * Stores the notification and one PENDING delivery per channel, in the caller's transaction. Nothing is sent
     * here: the delivery worker sends email, and the SSE push happens after commit.
     *
     * @return the notification, or empty if one with the same dedupe key already exists
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Notification> create(NewNotification request) {
        if (notifications.existsByDedupeKey(request.dedupeKey())) {
            log.info("Notification {} already exists, skipping", request.dedupeKey());
            return Optional.empty();
        }
        Instant now = Instant.now();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(request.userId());
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setBody(request.body());
        notification.setPayload(request.payload());
        notification.setDedupeKey(request.dedupeKey());
        notification.setSourceEventId(request.sourceEventId());
        notification.setCreatedAt(now);
        notifications.save(notification);

        for (DeliveryChannel channel : request.channels()) {
            deliveries.save(NotificationDelivery.pending(notification.getId(), channel, now));
        }
        events.publishEvent(new NotificationCreatedEvent(notification.getUserId(), NotificationView.of(notification)));
        return Optional.of(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationView> list(UUID userId, int page, int size) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page - 1, size))
            .map(NotificationView::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationView markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.NOTIFICATION_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationView.of(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now());
    }
}
