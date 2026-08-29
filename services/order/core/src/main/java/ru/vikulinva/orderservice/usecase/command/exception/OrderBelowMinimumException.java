package ru.vikulinva.orderservice.usecase.command.exception;

/** BR-013: минимальная сумма для confirm — 100 ₽. */
public final class OrderBelowMinimumException extends OrderDomainException {

    public OrderBelowMinimumException(String detail) {
        super("ORDER_BELOW_MINIMUM", 422, detail);
    }
}
