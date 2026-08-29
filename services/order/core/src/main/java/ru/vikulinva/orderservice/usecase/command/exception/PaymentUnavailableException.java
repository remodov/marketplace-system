package ru.vikulinva.orderservice.usecase.command.exception;

/** Payment Service временно недоступен (open circuit / network failure). */
public final class PaymentUnavailableException extends OrderDomainException {

    public PaymentUnavailableException(Throwable cause) {
        super("SERVICE_DEGRADED", 503, "Payment Service unavailable: " + cause.getMessage());
        initCause(cause);
    }
}
