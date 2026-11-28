package com.jobbeacon.notification.delivery;

import com.jobbeacon.notification.contact.UserContact;
import com.jobbeacon.notification.notification.Notification;
import com.jobbeacon.notification.provider.EmailMessage;
import com.jobbeacon.notification.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailChannelSender implements ChannelSender {

    private final EmailTemplateRenderer renderer;
    private final EmailProvider provider;

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, UserContact contact) {
        String html;
        try {
            html = renderer.render(notification, contact);
        } catch (RuntimeException e) {
            throw new PermanentDeliveryException("Cannot render email: " + e.getMessage(), e);
        }
        provider.send(new EmailMessage(contact.getEmail(), notification.getTitle(), html));
    }
}
