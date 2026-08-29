package ru.vikulinva.orderservice.adapter.out.postgres.outbox;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;
import ru.vikulinva.orderservice.service.DateTimeService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Outbox-relay. Периодически вычитывает строки из {@code outbox} с
 * {@code published_at IS NULL} и публикует во внешнюю шину через
 * {@link ExternalEventPublisher}. На успех — помечает {@code published_at = now()}.
 *
 * <p>Использует {@code SELECT ... FOR UPDATE SKIP LOCKED} — несколько
 * инстансов order-service могут безопасно работать параллельно.
 *
 * <p>Кейсы ошибок: {@code publish} может бросить исключение (Kafka
 * недоступен и т.п.) — транзакция откатывается, запись остаётся
 * непомеченной, при следующем тике попытка повторится.
 *
 * <p>Размер пачки и интервал — настраиваются через
 * {@code orderservice.outbox.batch-size} и
 * {@code orderservice.outbox.poll-interval-ms} (см. application.yml).
 */
@Component
@OutboundAdapter("Polls outbox table and forwards events to ExternalEventPublisher")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final DSLContext dsl;
    private final ExternalEventPublisher externalEventPublisher;
    private final DateTimeService dateTimeService;
    private final int batchSize;

    public OutboxRelay(DSLContext dsl,
                        ExternalEventPublisher externalEventPublisher,
                        DateTimeService dateTimeService,
                        @Value("${orderservice.outbox.batch-size:100}") int batchSize) {
        this.dsl = dsl;
        this.externalEventPublisher = externalEventPublisher;
        this.dateTimeService = dateTimeService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderservice.outbox.poll-interval-ms:1000}")
    public void poll() {
        try {
            int processed = relayBatch();
            if (processed > 0) {
                log.debug("Outbox relay processed {} events", processed);
            }
        } catch (Exception e) {
            log.warn("Outbox relay failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Один тик: вытащить пачку и опубликовать в одной транзакции, чтобы
     * блокировки {@code FOR UPDATE SKIP LOCKED} держались до коммита/отката.
     */
    @Transactional
    public int relayBatch() {
        List<OutboxRecord> batch = dsl
            .selectFrom(OUTBOX)
            .where(OUTBOX.PUBLISHED_AT.isNull())
            .orderBy(OUTBOX.OCCURRED_AT.asc())
            .limit(batchSize)
            .forUpdate()
            .skipLocked()
            .fetch();

        if (batch.isEmpty()) {
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.ofInstant(dateTimeService.now(), ZoneOffset.UTC);
        int published = 0;
        for (OutboxRecord record : batch) {
            externalEventPublisher.publish(
                record.getId(),
                record.getAggregateType(),
                record.getAggregateId(),
                record.getEventType(),
                record.getEventVersion(),
                record.getPayload().data(),
                record.getOccurredAt().toInstant());

            dsl.update(OUTBOX)
                .set(OUTBOX.PUBLISHED_AT, now)
                .where(OUTBOX.ID.eq(record.getId()))
                .execute();
            published++;
        }
        return published;
    }
}
