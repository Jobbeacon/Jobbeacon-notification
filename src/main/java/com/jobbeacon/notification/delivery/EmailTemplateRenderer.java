package com.jobbeacon.notification.delivery;

import com.jobbeacon.notification.config.FrontendProperties;
import com.jobbeacon.notification.contact.UserContact;
import com.jobbeacon.notification.notification.Notification;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders {@code templates/email/<type>.html}, e.g. {@code job-match-digest.html} for JOB_MATCH_DIGEST. */
@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    private final TemplateEngine templateEngine;
    private final FrontendProperties frontend;

    public String render(Notification notification, UserContact contact) {
        Context context = new Context(Locale.of("vi"));
        context.setVariable("notification", notification);
        context.setVariable("payload", notification.getPayload());
        context.setVariable("displayName", StringUtils.hasText(contact.getDisplayName()) ? contact.getDisplayName() : null);
        context.setVariable("frontendUrl", trimTrailingSlash(frontend.baseUrl()));
        return templateEngine.process(templateName(notification), context);
    }

    static String templateName(Notification notification) {
        return "email/" + notification.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
