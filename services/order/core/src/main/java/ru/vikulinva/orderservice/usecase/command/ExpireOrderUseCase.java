package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * Системный UC: просрочка ожидания оплаты ({@code PENDING_PAYMENT → EXPIRED}).
 * Без ABAC — вызывается планировщиком ExpireUnpaid.
 */
public record ExpireOrderUseCase(OrderId orderId) implements UseCaseCommand<Order> {

    public ExpireOrderUseCase {
        Objects.requireNonNull(orderId, "orderId");
    }
}
