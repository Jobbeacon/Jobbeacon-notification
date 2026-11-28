package com.jobbeacon.notification.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where events come from: the shared topic exchange and this service's queue bound to the routing keys it handles. */
@ConfigurationProperties(prefix = "app.rabbitmq")
public record EventsProperties(
    String exchange,
    String queue,
    String deadLetterExchange,
    String deadLetterQueue,
    List<String> routingKeys
) {
}
