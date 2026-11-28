package com.jobbeacon.notification.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the caller's Firebase uid from the gateway's {@code X-User-Id} header to a {@code String} parameter.
 * A request without the header is rejected with 401.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUid {
}
