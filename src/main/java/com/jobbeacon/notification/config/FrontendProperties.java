package com.jobbeacon.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URL of the web app, for links in emails. */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {
}
