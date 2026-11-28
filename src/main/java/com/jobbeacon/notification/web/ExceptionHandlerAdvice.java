package com.jobbeacon.notification.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ExceptionHandlerAdvice {

    private final MessageSource messageSource;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResponseGeneral<ErrorBody>> handleApiException(ApiException ex, Locale locale) {
        return error(ex.getStatus(), ex.getCode(), HttpHeaders.EMPTY, locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseGeneral<ErrorBody>> handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        String code = ex.getBindingResult().getFieldErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(ErrorCodes.BAD_REQUEST);
        return error(HttpStatus.BAD_REQUEST, code, HttpHeaders.EMPTY, locale);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ResponseGeneral<ErrorBody>> handleUnreadable(Exception ex, Locale locale) {
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, HttpHeaders.EMPTY, locale);
    }

    /** An SSE emitter that times out is a normal end of the stream, not an error; the client reconnects. */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout() {
    }

    /**
     * The client went away (e.g. closed the SSE tab) while we were writing. The response is unusable, so write
     * nothing and do not log it as an error.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        log.debug("Async request no longer usable: {}", ex.getMessage());
    }

    /** The client accepts none of the types we produce, so a JSON body cannot be written either: status only. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).build();
    }

    /**
     * Spring MVC exceptions (NoResourceFoundException 404, HttpRequestMethodNotSupportedException 405,
     * HttpMediaTypeNotSupportedException 415, MissingServletRequestParameterException / MissingRequestHeaderException
     * 400, ResponseStatusException, ...) carry their own status and headers (e.g. {@code Allow}); keep them instead of
     * turning them into 500.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
        HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
        ServletRequestBindingException.class, MissingServletRequestPartException.class,
        ErrorResponseException.class})
    public ResponseEntity<ResponseGeneral<ErrorBody>> handleErrorResponse(Exception ex, Locale locale,
                                                                          HttpServletResponse response) {
        return errorResponse((ErrorResponse) ex, ex, locale, response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseGeneral<ErrorBody>> handleAll(Exception ex, Locale locale,
                                                                HttpServletResponse response) {
        if (ex instanceof ErrorResponse errorResponse) {
            // any other Spring MVC exception that knows its own status
            return errorResponse(errorResponse, ex, locale, response);
        }
        if (response.isCommitted()) {
            // e.g. an SSE stream already sent its headers; a JSON body cannot be written any more.
            log.warn("Exception after response was committed: {}", ex.toString());
            log.debug("Committed-response exception detail", ex);
            return null;
        }
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, locale);
    }

    private ResponseEntity<ResponseGeneral<ErrorBody>> errorResponse(ErrorResponse errorResponse, Exception ex,
                                                                     Locale locale, HttpServletResponse response) {
        HttpStatusCode status = errorResponse.getStatusCode();
        if (response.isCommitted()) {
            // A body (or SSE headers) was already sent; nothing more can be written.
            log.debug("Error {} after response was committed: {}", status.value(), ex.toString());
            return null;
        }
        if (status.is5xxServerError()) {
            log.error("Server error {}: {}", status.value(), ex.getMessage(), ex);
        } else {
            log.debug("Client error {}: {}", status.value(), ex.getMessage());
        }
        return error(status, codeFor(status), errorResponse.getHeaders(), locale);
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCodes.UNAUTHORIZED;
            case 404 -> ErrorCodes.NOT_FOUND;
            case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCodes.INTERNAL_SERVER_ERROR : ErrorCodes.BAD_REQUEST;
        };
    }

    private ResponseEntity<ResponseGeneral<ErrorBody>> error(HttpStatusCode status, String code, HttpHeaders headers,
                                                             Locale locale) {
        String detail = messageSource.getMessage(code, null, code, locale);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
        return ResponseEntity.status(status)
            .headers(headers)
            .body(ResponseGeneral.of(status.value(), reason, new ErrorBody(code, detail)));
    }
}
