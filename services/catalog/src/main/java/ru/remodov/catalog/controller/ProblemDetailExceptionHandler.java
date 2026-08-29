package ru.remodov.catalog.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.remodov.catalog.exception.InvalidCurrencyException;
import ru.remodov.catalog.exception.InvalidPriceException;
import ru.remodov.catalog.exception.InvalidStateTransitionException;
import ru.remodov.catalog.exception.OwnProductRequiredException;
import ru.remodov.catalog.exception.ProductNotFoundException;

@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ProductNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Продукт не найден");
    }

    @ExceptionHandler(OwnProductRequiredException.class)
    public ResponseEntity<ProblemDetail> handleOwnRequired(OwnProductRequiredException e) {
        return problem(HttpStatus.NOT_FOUND, "OWN_PRODUCT_REQUIRED", "Продукт не найден");
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
            "Нельзя перевести продукт из " + e.from() + " в " + e.to());
    }

    @ExceptionHandler(InvalidPriceException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPrice(InvalidPriceException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Цена должна быть больше нуля");
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCurrency(InvalidCurrencyException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "Поддерживается только валюта RUB");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArg(IllegalArgumentException e) {
        var pd = problemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ошибка валидации входных данных");
        pd.setProperty("violations", List.of(violation(null, e.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        var pd = problemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ошибка валидации входных данных");
        var violations = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> violation(fe.getField(), fe.getDefaultMessage()))
            .toList();
        pd.setProperty("violations", violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformed(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Невозможно разобрать тело запроса");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Доступ запрещён");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется аутентификация");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        if (e instanceof AccessDeniedException ade) {
            return handleAccessDenied(ade);
        }
        if (e instanceof AuthenticationException ae) {
            return handleAuthentication(ae);
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
            "Внутренняя ошибка сервера");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problemDetail(status, code, detail));
    }

    private ProblemDetail problemDetail(HttpStatus status, String code, String detail) {
        var pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:problem:catalog:" + code));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }

    private record Violation(String field, String message) {}

    private static Violation violation(String field, String message) {
        return new Violation(field, message);
    }
}
