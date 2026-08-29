package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * Системный UC: финализация ({@code DELIVERED → COMPLETED}). Без ABAC —
 * вызывается планировщиком CloseDelivered через 14 дней после доставки.
 */
public record CompleteOrderUseCase(OrderId orderId) implements UseCaseCommand<Order> {

    public CompleteOrderUseCase {
        Objects.requireNonNull(orderId, "orderId");
    }
}
