package ru.vikulinva.orderservice.usecase.query;

import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

import java.util.Objects;

/**
 * UC-5 «Список заказов покупателя» с пагинацией и опциональным фильтром
 * по статусу. Сортировка — createdAt DESC.
 */
public record ListMyOrdersQuery(CustomerId requesterId,
                                 OrderStatus statusFilter,
                                 int page,
                                 int size)
    implements UseCaseQuery<PageResult<OrderSummary>> {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public ListMyOrdersQuery {
        Objects.requireNonNull(requesterId, "requesterId");
        if (page < 0) {
            throw new IllegalArgumentException("page must be ≥ 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1, " + MAX_SIZE + "]");
        }
    }

    public static ListMyOrdersQuery defaults(CustomerId requesterId) {
        return new ListMyOrdersQuery(requesterId, null, 0, DEFAULT_SIZE);
    }
}
