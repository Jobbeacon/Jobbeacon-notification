package com.jobbeacon.notification.security;

import com.jobbeacon.notification.web.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Trusts {@code X-User-Id} like the backend does: the gateway strips client-sent {@code X-User-*} headers and sets
 * them from the verified token, so this service must only be reachable through the gateway.
 */
public class CurrentUidArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUid.class) && parameter.getParameterType() == String.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String uid = webRequest.getHeader(GatewayHeaders.USER_ID);
        if (!StringUtils.hasText(uid)) {
            throw ApiException.unauthorized();
        }
        return uid.trim();
    }
}
