package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Заказ просрочен (PENDING_PAYMENT &gt; 15 мин) — {@code PENDING_PAYMENT → EXPIRED}.
 * Подписчик Inventory освобождает резерв.
 */
public final class OrderExpired extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final Instant expiredAt;

    public OrderExpired(OrderId orderId,
                         CustomerId customerId,
                         SellerId sellerId,
                         Instant expiredAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.expiredAt = Objects.requireNonNull(expiredAt, "expiredAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public Instant expiredAt() { return expiredAt; }
}
