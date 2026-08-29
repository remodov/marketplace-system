package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC «Открыть спор» — покупатель оспаривает доставленный заказ.
 * Перевод {@code DELIVERED → DISPUTE}.
 */
public record OpenDisputeUseCase(OrderId orderId, CustomerId requesterId, String reason)
    implements UseCaseCommand<Order> {

    public OpenDisputeUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
