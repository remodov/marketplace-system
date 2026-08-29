package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC-3 «Отмена заказа» (V1: только до оплаты).
 *
 * <p>Перевод {@code DRAFT|PENDING_PAYMENT → CANCELLED}, публикация
 * {@code OrderCancelled} (Outbox → Inventory/Notification).
 */
public record CancelOrderUseCase(OrderId orderId,
                                  CustomerId requesterId,
                                  CancellationReason reason)
    implements UseCaseCommand<Order> {

    public CancelOrderUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(reason, "reason");
    }
}
