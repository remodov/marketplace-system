package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.List;
import java.util.Objects;

/**
 * Заказ создан в статусе DRAFT. Срабатывает при {@code Order.create(...)}.
 * См. §8 спеки. Внутреннее событие — обновляет Read Model {@code order_summaries}
 * через {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
public final class OrderCreated extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final Money total;
    private final int itemsCount;
    private final List<ItemSnapshot> items;

    public OrderCreated(OrderId orderId,
                         CustomerId customerId,
                         SellerId sellerId,
                         Money total,
                         List<ItemSnapshot> items) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.total = Objects.requireNonNull(total, "total");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.itemsCount = this.items.size();
    }

    public CustomerId customerId() {
        return customerId;
    }

    public SellerId sellerId() {
        return sellerId;
    }

    public Money total() {
        return total;
    }

    public int itemsCount() {
        return itemsCount;
    }

    public List<ItemSnapshot> items() {
        return items;
    }

    /** Слепок позиции на момент создания — без ссылок на доменные объекты. */
    public record ItemSnapshot(java.util.UUID productId, int quantity, Money unitPrice) {
        public ItemSnapshot {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be >= 1: " + quantity);
            }
        }
    }
}
