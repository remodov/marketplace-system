package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/** Заказ передан в доставку — переход {@code PAID → SHIPPED}. */
public final class OrderShipped extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final String trackingNumber;
    private final Instant shippedAt;

    public OrderShipped(OrderId orderId,
                         CustomerId customerId,
                         SellerId sellerId,
                         String trackingNumber,
                         Instant shippedAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.shippedAt = Objects.requireNonNull(shippedAt, "shippedAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public String trackingNumber() { return trackingNumber; }
    public Instant shippedAt() { return shippedAt; }
}
