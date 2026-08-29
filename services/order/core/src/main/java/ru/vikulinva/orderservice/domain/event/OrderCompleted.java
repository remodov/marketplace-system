package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Заказ финализирован — {@code DELIVERED → COMPLETED} (терминальное).
 * Через 14 дней после delivery, если не было дисптуа.
 */
public final class OrderCompleted extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final Instant closedAt;

    public OrderCompleted(OrderId orderId,
                           CustomerId customerId,
                           SellerId sellerId,
                           Instant closedAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public Instant closedAt() { return closedAt; }
}
