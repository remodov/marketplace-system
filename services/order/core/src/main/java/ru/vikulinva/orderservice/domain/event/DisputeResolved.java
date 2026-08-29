package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Оператор закрыл спор. Решение оператора:
 * <ul>
 *   <li>{@code COMPLETED} — спор отклонён, заказ финализируется в пользу продавца;</li>
 *   <li>{@code REFUNDED} — спор удовлетворён, инициируется возврат средств покупателю.</li>
 * </ul>
 *
 * Финальный статус заказа сохраняется в {@code finalStatus}; refund-сага
 * (если применимо) запускается подписчиком на это событие.
 */
public final class DisputeResolved extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final OrderStatus finalStatus;
    private final String resolutionNote;
    private final Instant resolvedAt;

    public DisputeResolved(OrderId orderId,
                            CustomerId customerId,
                            SellerId sellerId,
                            OrderStatus finalStatus,
                            String resolutionNote,
                            Instant resolvedAt) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.finalStatus = Objects.requireNonNull(finalStatus, "finalStatus");
        this.resolutionNote = Objects.requireNonNull(resolutionNote, "resolutionNote");
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public OrderStatus finalStatus() { return finalStatus; }
    public String resolutionNote() { return resolutionNote; }
    public Instant resolvedAt() { return resolvedAt; }
}
