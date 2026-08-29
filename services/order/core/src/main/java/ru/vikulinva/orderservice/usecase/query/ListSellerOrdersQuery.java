package ru.vikulinva.orderservice.usecase.query;

import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

import java.util.Objects;

/**
 * UC «Список заказов продавца» — для seller-кабинета. Пагинация и фильтр
 * по статусу аналогичны {@link ListMyOrdersQuery}.
 */
public record ListSellerOrdersQuery(SellerId requesterSellerId,
                                      OrderStatus statusFilter,
                                      int page,
                                      int size)
    implements UseCaseQuery<PageResult<OrderSummary>> {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public ListSellerOrdersQuery {
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        if (page < 0) {
            throw new IllegalArgumentException("page must be ≥ 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1, " + MAX_SIZE + "]");
        }
    }
}
