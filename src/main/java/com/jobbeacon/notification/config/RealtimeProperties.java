package com.jobbeacon.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(Duration emitterTimeout, Duration heartbeatInterval) {
}
