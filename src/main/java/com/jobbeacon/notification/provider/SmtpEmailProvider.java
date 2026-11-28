package com.jobbeacon.notification.provider;

import com.jobbeacon.notification.config.EmailProperties;
import com.jobbeacon.notification.delivery.PermanentDeliveryException;
import com.jobbeacon.notification.delivery.RetryableDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends through SMTP with {@link JavaMailSender} (Mailpit locally, any SMTP server in prod). The From address is
 * validated at startup: a bad {@code app.mail.from} fails fast instead of marking every delivery FAILED as if the
 * recipient were wrong.
 */
@Component
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    public SmtpEmailProvider(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.from = parseFrom(properties.from());
    }

    static InternetAddress parseFrom(String from) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.mail.from must be set");
        }
        try {
            InternetAddress address = new InternetAddress(from.trim(), true);
            address.validate();
            return address;
        } catch (AddressException e) {
            throw new IllegalStateException("app.mail.from is not a valid address: " + from, e);
        }
    }

    @Override
    public void send(EmailMessage message) {
        MimeMessage mime;
        try {
            mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
        } catch (MessagingException e) {
            // From is validated at startup, so this is about the recipient or the content.
            throw new PermanentDeliveryException("Cannot build email: " + e.getMessage(), e);
        }
        try {
            mailSender.send(mime);
        } catch (MailParseException | MailPreparationException e) {
            throw new PermanentDeliveryException("Cannot build email: " + e.getMessage(), e);
        } catch (MailSendException e) {
            if (rejectedAddress(e)) {
                throw new PermanentDeliveryException("Recipient rejected: " + e.getMessage(), e);
            }
            throw new RetryableDeliveryException("SMTP send failed: " + e.getMessage(), e);
        } catch (MailException e) {
            // Authentication and connection problems are configuration or outage issues: retry.
            throw new RetryableDeliveryException("SMTP error: " + e.getMessage(), e);
        }
    }

    /** The server refused the recipient itself (unknown address), which retrying will not fix. */
    private static boolean rejectedAddress(MailSendException e) {
        for (Exception failure : e.getFailedMessages().values()) {
            if (failure instanceof SendFailedException sendFailed
                && sendFailed.getInvalidAddresses() != null && sendFailed.getInvalidAddresses().length > 0) {
                return true;
            }
        }
        return false;
    }
}
