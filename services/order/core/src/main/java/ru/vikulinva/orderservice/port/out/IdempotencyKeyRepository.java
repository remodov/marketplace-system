package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

import java.time.Instant;
import java.util.Optional;

/**
 * Порт хранилища идемпотентных ключей (BR-010). Реализация — в
 * {@code adapter-out-postgres}.
 */
@OutboundPort
public interface IdempotencyKeyRepository {

    /**
     * Найти ранее сохранённый результат по ключу + хешу запроса.
     *
     * @param idempotencyKey ключ из заголовка Idempotency-Key
     * @param requestHash    sha256 от тела запроса
     * @return {@code Optional} с orderId, если ключ виден и хеш совпадает
     * @throws ru.vikulinva.orderservice.usecase.command.exception.IdempotencyKeyConflictException
     *         если ключ виден, но хеш отличается (тот же ключ для другого запроса)
     */
    Optional<OrderId> find(String idempotencyKey, String requestHash);

    /**
     * Сохранить ключ + соответствующий orderId. Вызывается после успешного
     * создания заказа в той же транзакции, что и сам Order.
     */
    void save(String idempotencyKey, String requestHash, OrderId orderId, Instant createdAt);
}
