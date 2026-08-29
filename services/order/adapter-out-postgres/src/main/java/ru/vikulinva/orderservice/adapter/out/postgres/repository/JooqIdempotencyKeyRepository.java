package ru.vikulinva.orderservice.adapter.out.postgres.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.IdempotencyKeysPojo;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.IdempotencyKeyRepository;
import ru.vikulinva.orderservice.usecase.command.exception.IdempotencyKeyConflictException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.IDEMPOTENCY_KEYS;

/**
 * jOOQ-реализация порта {@link IdempotencyKeyRepository}. Соблюдает BR-010.
 */
@Component
@OutboundAdapter("Idempotency keys storage")
public class JooqIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final DSLContext dsl;

    public JooqIdempotencyKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<OrderId> find(String idempotencyKey, String requestHash) {
        IdempotencyKeysPojo row = dsl.selectFrom(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOneInto(IdempotencyKeysPojo.class);
        if (row == null) {
            return Optional.empty();
        }
        if (!row.getRequestHash().equals(requestHash)) {
            // BR-010: тот же ключ для другого тела запроса — конфликт.
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }
        return Optional.of(OrderId.of(row.getOrderId()));
    }

    @Override
    public void save(String idempotencyKey, String requestHash, OrderId orderId, Instant createdAt) {
        dsl.insertInto(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, idempotencyKey)
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.ORDER_ID, orderId.value())
            .set(IDEMPOTENCY_KEYS.CREATED_AT, createdAt.atOffset(ZoneOffset.UTC))
            .execute();
    }
}
