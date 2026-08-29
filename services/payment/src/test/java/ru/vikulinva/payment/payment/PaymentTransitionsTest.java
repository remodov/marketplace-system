package ru.vikulinva.payment.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.vikulinva.payment.payment.Payment.Status.AUTHORIZED;
import static ru.vikulinva.payment.payment.Payment.Status.CAPTURED;
import static ru.vikulinva.payment.payment.Payment.Status.FAILED;
import static ru.vikulinva.payment.payment.Payment.Status.REFUNDED;

/**
 * Автомат статусов проверяется без Spring и без базы: это чистое правило.
 */
class PaymentTransitionsTest {

    @Test
    @DisplayName("разрешено ровно то, что описано в модели")
    void allowedTransitions() {
        assertThat(AUTHORIZED.canMoveTo(CAPTURED)).isTrue();
        assertThat(AUTHORIZED.canMoveTo(REFUNDED)).isTrue();
        assertThat(AUTHORIZED.canMoveTo(FAILED)).isTrue();
        assertThat(CAPTURED.canMoveTo(REFUNDED)).isTrue();
    }

    @Test
    @DisplayName("конечные статусы никуда не ведут")
    void terminalStatesAreTerminal() {
        assertThat(REFUNDED.canMoveTo(CAPTURED)).isFalse();
        assertThat(REFUNDED.canMoveTo(AUTHORIZED)).isFalse();
        assertThat(FAILED.canMoveTo(CAPTURED)).isFalse();
        assertThat(CAPTURED.canMoveTo(AUTHORIZED)).isFalse();
    }

    @Test
    @DisplayName("переход в себя же запрещён — повтор обрабатывается выше, а не в автомате")
    void selfTransitionIsNotAllowed() {
        assertThat(AUTHORIZED.canMoveTo(AUTHORIZED)).isFalse();
        assertThat(CAPTURED.canMoveTo(CAPTURED)).isFalse();
        assertThat(REFUNDED.canMoveTo(REFUNDED)).isFalse();
    }
}
