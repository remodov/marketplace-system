package ru.vikulinva.customer.adapter.out.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.customer.persistence.generated.tables.records.OutboxRecord;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static ru.vikulinva.customer.persistence.generated.Tables.OUTBOX;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "customer.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxRelay {

    private static final String TOPIC = "customer.events.v1";
    private static final int BATCH_SIZE = 100;

    private final DSLContext dslContext;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${customer.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxRecord> batch = dslContext.selectFrom(OUTBOX)
                .where(OUTBOX.PUBLISHED_AT.isNull())
                .orderBy(OUTBOX.ID.asc())
                .limit(BATCH_SIZE)
                .forUpdate()
                .skipLocked()
                .fetch();

        if (batch.isEmpty()) {
            return;
        }

        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        for (OutboxRecord row : batch) {
            kafkaTemplate.send(TOPIC, row.getAggregateId().toString(), row.getPayload().data());
            row.setPublishedAt(now);
            row.store();
        }
        log.debug("published {} outbox events to {}", batch.size(), TOPIC);
    }
}
