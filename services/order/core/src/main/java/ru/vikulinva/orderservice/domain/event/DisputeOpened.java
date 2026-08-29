package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Покупатель открыл спор по доставленному заказу — {@code DELIVERED → DISPUTE}.
 * Подписчики: оператор-кабинет, Notification.
 */
public final class DisputeOpened extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final String reason;
    private final Instant openedAt;

    public DisputeOpened(OrderId orderId,
                          CustomerId customerId,
                          SellerId sellerId,
                          String reason,
                          Instant openedAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public String reason() { return reason; }
    public Instant openedAt() { return openedAt; }
}
