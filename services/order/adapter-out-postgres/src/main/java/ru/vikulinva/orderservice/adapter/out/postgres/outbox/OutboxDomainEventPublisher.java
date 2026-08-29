package ru.vikulinva.orderservice.adapter.out.postgres.outbox;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.ddd.DomainEventPublisher;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.adapter.out.postgres.mapper.EventPayloadSerializer;

import java.util.UUID;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Реализация {@link DomainEventPublisher}, пишущая события в таблицу {@code outbox}.
 *
 * <p>Гарантия атомарности (BR-015): метод вызывается из репозитория в той же
 * Spring-транзакции, что и сохранение агрегата. Если транзакция откатится —
 * события не «уйдут».
 *
 * <p>Outbox-relay (отдельная {@code @Scheduled}-job) забирает строки с
 * {@code published_at IS NULL} и публикует в Kafka.
 */
@Component
@OutboundAdapter("Outbox-based DomainEventPublisher")
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final DSLContext dsl;
    private final EventPayloadSerializer payloadSerializer;

    public OutboxDomainEventPublisher(DSLContext dsl, EventPayloadSerializer payloadSerializer) {
        this.dsl = dsl;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void publish(DomainEvent event) {
        OutboxRecord record = dsl.newRecord(OUTBOX);
        record.setId(event.getId());
        record.setAggregateId(UUID.fromString(event.getAggregateId()));
        record.setAggregateType(event.getAggregateType());
        record.setEventType(event.getClass().getSimpleName());
        record.setEventVersion(1);
        record.setPayload(payloadSerializer.toPayload(event));
        record.setOccurredAt(event.getCreatedAt());
        record.setPublishedAt(null);
        record.store();
    }
}
