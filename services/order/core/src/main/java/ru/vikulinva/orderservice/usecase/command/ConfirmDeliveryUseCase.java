package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC-8 «Подтверждение получения» (покупатель). Перевод {@code SHIPPED → DELIVERED}.
 */
public record ConfirmDeliveryUseCase(OrderId orderId, CustomerId requesterId)
    implements UseCaseCommand<Order> {

    public ConfirmDeliveryUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
