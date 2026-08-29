package ru.vikulinva.orderservice.usecase.query.dto;

import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;

/**
 * Лёгкий read-проекция заказа для списков. Не агрегат — без позиций и
 * адреса, только основные поля для UI/админки.
 */
public record OrderSummary(
    OrderId id,
    CustomerId customerId,
    SellerId sellerId,
    OrderStatus status,
    Money total,
    int itemsCount,
    Instant createdAt
) {
}
