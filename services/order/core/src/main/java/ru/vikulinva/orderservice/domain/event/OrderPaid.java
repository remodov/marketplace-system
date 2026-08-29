package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Заказ оплачен — переход {@code PENDING_PAYMENT → PAID}. Подписчики:
 * Notification, аналитика, Logistics (готовит доставку).
 */
public final class OrderPaid extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final UUID paymentId;
    private final Money total;
    private final Instant paidAt;

    public OrderPaid(OrderId orderId,
                      CustomerId customerId,
                      SellerId sellerId,
                      UUID paymentId,
                      Money total,
                      Instant paidAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.total = Objects.requireNonNull(total, "total");
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public UUID paymentId() { return paymentId; }
    public Money total() { return total; }
    public Instant paidAt() { return paidAt; }
}
