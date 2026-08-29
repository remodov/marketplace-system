package ru.vikulinva.customer.adapter.in.rest.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vikulinva.customer.core.customer.domain.exception.CustomerNotFoundException;
import ru.vikulinva.customer.core.customer.domain.exception.EmailAlreadyRegisteredException;
import ru.vikulinva.customer.core.customer.domain.exception.InvalidStatusTransitionException;
import ru.vikulinva.customer.core.customer.domain.exception.OptimisticLockException;
import ru.vikulinva.customer.core.customer.domain.exception.ProfileUpdateForbiddenStatusException;
import ru.vikulinva.customer.core.customer.domain.exception.TokenInvalidOrExpiredException;
import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        log.warn("email conflict");
        return problem(HttpStatus.CONFLICT, "Email already registered", "EMAIL_ALREADY_REGISTERED", e.getMessage());
    }

    @ExceptionHandler(TokenInvalidOrExpiredException.class)
    public ProblemDetail handleTokenInvalid(TokenInvalidOrExpiredException e) {
        log.warn("verification token invalid or expired");
        return problem(HttpStatus.GONE, "Verification token expired or invalid", "TOKEN_EXPIRED_OR_INVALID",
                "verification link is no longer valid");
    }

    @ExceptionHandler(ProfileUpdateForbiddenStatusException.class)
    public ProblemDetail handleProfileUpdateForbidden(ProfileUpdateForbiddenStatusException e) {
        log.warn("profile update forbidden in status {}", e.getCurrentStatus());
        return problem(HttpStatus.CONFLICT, "Profile update forbidden", "PROFILE_UPDATE_FORBIDDEN_STATUS", e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException e) {
        log.warn("invalid status transition {} -> {}", e.getFrom(), e.getTo());
        return problem(HttpStatus.CONFLICT, "Invalid status transition", "INVALID_STATUS_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockException e) {
        log.warn("optimistic lock conflict for customer {}", e.getCustomerId().value());
        return problem(HttpStatus.CONFLICT, "Concurrent update conflict", "OPTIMISTIC_LOCK_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleNotFound(CustomerNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Customer not found", "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleDomainValidation(ValidationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
                "request body validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN", "access denied");
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
