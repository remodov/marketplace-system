package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC «Закрыть спор» (оператор). Решение — финальный статус
 * ({@code COMPLETED} либо {@code REFUNDED}) с пояснением.
 */
public record ResolveDisputeUseCase(OrderId orderId,
                                      OrderStatus finalStatus,
                                      String resolutionNote)
    implements UseCaseCommand<Order> {

    public ResolveDisputeUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(finalStatus, "finalStatus");
        Objects.requireNonNull(resolutionNote, "resolutionNote");
        if (finalStatus != OrderStatus.COMPLETED && finalStatus != OrderStatus.REFUNDED) {
            throw new IllegalArgumentException(
                "finalStatus must be COMPLETED or REFUNDED, got: " + finalStatus);
        }
    }
}
