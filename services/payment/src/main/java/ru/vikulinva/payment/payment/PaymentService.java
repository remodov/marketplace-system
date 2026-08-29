package ru.vikulinva.payment.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Заказ платят один раз. Повторный вызов по тому же заказу возвращает
     * уже созданный платёж: сага при повторе не должна списывать деньги дважды.
     */
    @Transactional
    public Payment authorize(UUID orderId, BigDecimal amount, String currency) {
        // TODO шаг 11: заказ платят один раз. Сага повторяет шаги при сбоях —
        // повторная авторизация того же заказа не должна создавать второй платёж.
        Instant now = Instant.now(clock);
        Payment payment = new Payment(UUID.randomUUID(), orderId, amount, currency,
            Payment.Status.AUTHORIZED, now, now);
        repository.insert(payment);
        return payment;
    }

    @Transactional
    public Payment capture(UUID paymentId) {
        return moveTo(paymentId, Payment.Status.CAPTURED);
    }

    /**
     * Компенсация саги: деньги возвращаются. Повторный возврат — не ошибка
     * и не второй возврат: сага повторяет шаги, и повтор обязан быть безопасным.
     */
    @Transactional
    public Payment refund(UUID paymentId) {
        // TODO шаг 11: компенсация саги. Повторный возврат — это не второй возврат
        // и не ошибка: сага повторяет шаг, деньги возвращаются один раз.
        return moveTo(paymentId, Payment.Status.REFUNDED);
    }

    @Transactional(readOnly = true)
    public Payment byId(UUID paymentId) {
        return repository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private Payment moveTo(UUID paymentId, Payment.Status next) {
        Payment moved = byId(paymentId).moveTo(next, Instant.now(clock));
        repository.updateStatus(moved);
        return moved;
    }
}
