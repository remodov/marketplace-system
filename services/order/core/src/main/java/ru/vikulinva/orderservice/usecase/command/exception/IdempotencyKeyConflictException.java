package ru.vikulinva.orderservice.usecase.command.exception;

/** Idempotency-Key уже используется для другого тела запроса. */
public final class IdempotencyKeyConflictException extends OrderDomainException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("IDEMPOTENCY_KEY_CONFLICT", 409,
            "Idempotency-Key '" + idempotencyKey + "' is reused with a different request body");
    }
}
