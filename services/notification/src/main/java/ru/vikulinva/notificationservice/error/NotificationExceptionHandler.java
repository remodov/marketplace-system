package ru.vikulinva.notificationservice.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Конвертирует доменные исключения в RFC 9457 ProblemDetails.
 */
@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ProblemDetail> handle(NotificationException ex, HttpServletRequest request) {
        var pd = ProblemDetail.forStatus(HttpStatus.valueOf(ex.httpStatus()));
        pd.setType(URI.create("https://vikulin-va.ru/errors/" + ex.code().toLowerCase().replace('_', '-')));
        pd.setTitle(humanTitle(ex.code()));
        pd.setDetail(ex.getMessage());
        pd.setProperty("code", ex.code());
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity
            .status(HttpStatus.valueOf(ex.httpStatus()))
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(pd);
    }

    private static String humanTitle(String code) {
        return switch (code) {
            case "NOTIFICATION_NOT_FOUND" -> "Notification not found";
            case "INVALID_STATUS_FOR_RETRY" -> "Notification is not in a retriable state";
            case "WEBHOOK_SIGNATURE_INVALID" -> "Webhook signature is invalid";
            default -> "Notification domain error";
        };
    }
}
