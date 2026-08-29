package ru.vikulinva.orderservice.usecase.command.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;

/** Команда применена к неподходящему статусу заказа. */
public final class OrderInvalidStateException extends OrderDomainException {

    public OrderInvalidStateException(OrderStatus current, OrderStatus expected, String action) {
        super("ORDER_INVALID_STATE", 409,
            "Cannot %s: order is in %s, expected %s".formatted(action, current, expected));
    }

    public OrderInvalidStateException(String message) {
        super("ORDER_INVALID_STATE", 409, message);
    }
}
