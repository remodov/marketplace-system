package ru.vikulinva.orderservice.domain.valueobject;

/**
 * Order lifecycle states. See spec §4 «Жизненный цикл и состояния».
 *
 * Terminal states: {@link #COMPLETED}, {@link #EXPIRED}, {@link #REFUNDED}.
 */
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    DISPUTE,
    REFUNDED;

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == REFUNDED;
    }
}
