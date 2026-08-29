package ru.vikulinva.orderservice.adapter.out.postgres.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.port.out.ProcessedEventsRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.PROCESSED_EVENTS;

/**
 * jOOQ-реализация {@link ProcessedEventsRepository}. Уникальный ключ по
 * {@code event_id} — конфликт игнорируем (повторная доставка).
 */
@Component
@OutboundAdapter("Idempotent consumer journal (processed_events)")
public class JooqProcessedEventsRepository implements ProcessedEventsRepository {

    private final DSLContext dsl;

    public JooqProcessedEventsRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public boolean markProcessed(UUID eventId, String eventType, Instant processedAt) {
        OffsetDateTime offset = OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC);
        int inserted = dsl.insertInto(PROCESSED_EVENTS)
            .set(PROCESSED_EVENTS.EVENT_ID, eventId)
            .set(PROCESSED_EVENTS.EVENT_TYPE, eventType)
            .set(PROCESSED_EVENTS.PROCESSED_AT, offset)
            .onConflict(PROCESSED_EVENTS.EVENT_ID).doNothing()
            .execute();
        return inserted > 0;
    }
}
