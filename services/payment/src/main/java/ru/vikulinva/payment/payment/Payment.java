package ru.vikulinva.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(UUID id, UUID orderId, BigDecimal amount, String currency,
                      Status status, Instant createdAt, Instant updatedAt) {

    public enum Status {
        AUTHORIZED,
        CAPTURED,
        REFUNDED,
        FAILED;

        /**
         * Разрешённые переходы. Всё, чего здесь нет, — запрещено:
         * запрет по умолчанию честнее, чем перечисление запретов.
         */
        public boolean canMoveTo(Status next) {
            return switch (this) {
                case AUTHORIZED -> next == CAPTURED || next == REFUNDED || next == FAILED;
                case CAPTURED -> next == REFUNDED;
                case REFUNDED, FAILED -> false;
            };
        }
    }

    public Payment moveTo(Status next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidPaymentTransitionException(id, status, next);
        }
        return new Payment(id, orderId, amount, currency, next, createdAt, now);
    }
}
