package ru.vikulinva.notificationservice.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import static ru.vikulinva.notificationservice.generated.Tables.PROCESSED_EVENTS;

/**
 * Журнал обработанных Kafka-событий для идемпотентного консьюмера. См. BR-N1.
 */
@Repository
public class ProcessedEventsRepository {

    private final DSLContext dsl;

    public ProcessedEventsRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /**
     * @return {@code true} если запись только что вставлена; {@code false} если
     *         событие уже было обработано (повторная доставка).
     */
    public boolean markProcessed(UUID eventId, String eventType, OffsetDateTime processedAt) {
        int inserted = dsl.insertInto(PROCESSED_EVENTS)
            .set(PROCESSED_EVENTS.EVENT_ID, eventId)
            .set(PROCESSED_EVENTS.EVENT_TYPE, eventType)
            .set(PROCESSED_EVENTS.PROCESSED_AT, processedAt)
            .onConflict(PROCESSED_EVENTS.EVENT_ID).doNothing()
            .execute();
        return inserted > 0;
    }
}
