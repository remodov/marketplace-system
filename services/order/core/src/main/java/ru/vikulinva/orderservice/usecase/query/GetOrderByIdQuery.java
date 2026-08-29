package ru.vikulinva.orderservice.usecase.query;

import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

import java.util.Objects;

/**
 * UC-4 «Чтение заказа по идентификатору».
 *
 * <p>ABAC: только владелец заказа ({@code requesterId}) видит свой заказ;
 * для чужого выбрасывается {@code OrderNotFoundException} (скрываем
 * существование). Admin/seller-просмотр — отдельный UC.
 */
public record GetOrderByIdQuery(OrderId orderId, CustomerId requesterId)
    implements UseCaseQuery<Order> {

    public GetOrderByIdQuery {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
