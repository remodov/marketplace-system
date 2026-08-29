package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.Objects;

/**
 * UC-7 «Передача в доставку» (продавец). Перевод {@code PAID → SHIPPED}.
 *
 * <p>ABAC: {@code requesterSellerId} должен совпадать с {@code Order.sellerId};
 * иначе — {@code OrderNotFoundException} (скрываем чужие заказы продавцов).
 */
public record MarkShippedUseCase(OrderId orderId,
                                  SellerId requesterSellerId,
                                  String trackingNumber)
    implements UseCaseCommand<Order> {

    public MarkShippedUseCase {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        if (trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
    }
}
