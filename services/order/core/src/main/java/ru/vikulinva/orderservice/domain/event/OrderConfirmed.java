package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Заказ подтверждён покупателем — переход {@code DRAFT → PENDING_PAYMENT}.
 * Внешнее событие, публикуется через Outbox в топик {@code marketplace.orders.v1}.
 *
 * <p>Подписчики: Inventory (резервирует остаток → отвечает {@code ItemReserved} /
 * {@code ReservationFailed}), Notification.
 */
public final class OrderConfirmed extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final Money total;
    private final List<ItemSnapshot> items;

    public OrderConfirmed(OrderId orderId,
                           CustomerId customerId,
                           SellerId sellerId,
                           List<ItemSnapshot> items,
                           Money total) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.total = Objects.requireNonNull(total, "total");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public Money total() { return total; }
    public List<ItemSnapshot> items() { return items; }

    public record ItemSnapshot(UUID productId, int quantity, Money unitPrice) {

        public ItemSnapshot {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be >= 1: " + quantity);
            }
        }
    }
}
