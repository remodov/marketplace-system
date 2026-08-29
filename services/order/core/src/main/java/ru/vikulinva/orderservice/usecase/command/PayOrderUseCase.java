package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-6 «Подтверждение оплаты». Триггерится вебхуком от Payment Service
 * после успешного списания. Перевод {@code PENDING_PAYMENT → PAID},
 * публикация {@code OrderPaid}.
 *
 * <p>Идемпотентен: если заказ уже PAID с тем же {@code paymentId} —
 * возвращается существующий заказ без событий (повторный вебхук).
 */
public record PayOrderUseCase(OrderId orderId, UUID paymentId)
    implements UseCaseCommand<Order> {

    public PayOrderUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
