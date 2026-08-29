package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC-2 «Подтверждение заказа». Перевод {@code DRAFT → PENDING_PAYMENT} и
 * публикация {@code OrderConfirmed} (Outbox → Inventory/Notification).
 *
 * <p>ABAC: {@code requesterId} должен совпадать с владельцем заказа
 * ({@code Order.customerId}); иначе — {@code OrderNotFoundException} (скрываем
 * существование чужого заказа).
 *
 * <p>Инварианты подтверждения проверяются в {@link Order#confirm()}:
 * статус DRAFT, BR-013 минимальная сумма.
 */
public record ConfirmOrderUseCase(OrderId orderId, CustomerId requesterId)
    implements UseCaseCommand<Order> {

    public ConfirmOrderUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
