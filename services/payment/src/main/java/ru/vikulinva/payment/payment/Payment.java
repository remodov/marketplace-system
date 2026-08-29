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
         * TODO шаг 11: разрешённые переходы платежа.
         * Перечисляй разрешённое, а не запрещённое: список запретов забывают
         * дополнить, и дыра появляется молча.
         */
        public boolean canMoveTo(Status next) {
            return true;
        }
    }

    public Payment moveTo(Status next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidPaymentTransitionException(id, status, next);
        }
        return new Payment(id, orderId, amount, currency, next, createdAt, now);
    }
}
