package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

import java.util.UUID;

/**
 * Порт «Payment Service». Используется для refund-саги при отмене
 * оплаченного заказа. Обычная оплата идёт асинхронно: мы публикуем
 * {@code OrderConfirmed}, Payment создаёт интент и присылает webhook
 * о завершении — sync HTTP-вызовы к Payment здесь не нужны.
 */
@OutboundPort
public interface PaymentPort {

    /**
     * Запрос рефанда. Идемпотентен по {@code idempotencyKey} (например,
     * {@code "refund-" + orderId}). На стороне Payment реальный возврат
     * выполняется асинхронно — здесь только инициируем заявку.
     *
     * @return id созданной заявки на возврат
     * @throws ru.vikulinva.orderservice.usecase.command.exception.PaymentUnavailableException
     *         если Payment недоступен (open circuit / network error)
     */
    UUID requestRefund(OrderId orderId, UUID paymentId, Money amount, String idempotencyKey);
}
