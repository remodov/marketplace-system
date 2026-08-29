package ru.vikulinva.orderservice.usecase.command.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Заказ не существует или скрыт ABAC. */
public final class OrderNotFoundException extends OrderDomainException {

    public OrderNotFoundException(OrderId id) {
        super("ORDER_NOT_FOUND", 404, "Order " + id.value() + " not found");
    }
}
