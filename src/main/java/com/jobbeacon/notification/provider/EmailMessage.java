package com.jobbeacon.notification.provider;

/** What an {@link EmailProvider} needs, independent of the library that sends it. */
public record EmailMessage(String to, String subject, String html) {
}
