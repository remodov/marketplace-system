package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/** Доставка подтверждена покупателем — {@code SHIPPED → DELIVERED}. */
public final class OrderDelivered extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final Instant deliveredAt;

    public OrderDelivered(OrderId orderId,
                           CustomerId customerId,
                           SellerId sellerId,
                           Instant deliveredAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public Instant deliveredAt() { return deliveredAt; }
}
