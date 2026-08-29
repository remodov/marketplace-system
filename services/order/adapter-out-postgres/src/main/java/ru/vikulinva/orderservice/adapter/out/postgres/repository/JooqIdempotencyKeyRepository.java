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
        // TODO шаг 9: найти запись по ключу. Три исхода, а не два:
        // ключа нет; ключ есть и тело то же; ключ есть, а тело другое.
        return Optional.empty();
    }

    @Override
    public void save(String idempotencyKey, String requestHash, OrderId orderId, Instant createdAt) {
        // TODO шаг 9: сохранить ключ, хеш тела и идентификатор заказа.
        // Таблица idempotency_keys уже есть в миграциях.
    }
}
