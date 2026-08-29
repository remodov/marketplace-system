package ru.vikulinva.orderservice.domain.entity;

import ru.vikulinva.ddd.Entity;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.Objects;

/**
 * Позиция заказа — внутренняя сущность агрегата {@link ru.vikulinva.orderservice.domain.aggregate.Order}.
 * Уникальна в рамках агрегата по {@code (productId, sellerId)} (см. §3.1 спеки).
 *
 * <p>Equals/hashCode наследуются из {@link Entity} — сравнение по {@code id}.
 * Не меняем эти методы (R-ENT-X1).
 */
public final class OrderItem extends Entity<OrderItemId> {

    private final OrderItemId id;
    private final ProductId productId;
    private final SellerId sellerId;
    private final Quantity quantity;
    private final Money unitPrice;

    public OrderItem(OrderItemId id,
                      ProductId productId,
                      SellerId sellerId,
                      Quantity quantity,
                      Money unitPrice) {
        this.id = Objects.requireNonNull(id, "OrderItem.id");
        this.productId = Objects.requireNonNull(productId, "OrderItem.productId");
        this.sellerId = Objects.requireNonNull(sellerId, "OrderItem.sellerId");
        this.quantity = Objects.requireNonNull(quantity, "OrderItem.quantity");
        this.unitPrice = Objects.requireNonNull(unitPrice, "OrderItem.unitPrice");
    }

    @Override
    public OrderItemId getId() {
        return id;
    }

    public ProductId productId() {
        return productId;
    }

    public SellerId sellerId() {
        return sellerId;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    /** Стоимость позиции = unitPrice × quantity. */
    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }
}
