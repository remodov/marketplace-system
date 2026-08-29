package ru.vikulinva.customer.persistence.adapter.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep5;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import ru.vikulinva.ddd.DomainEvent;

import java.util.List;
import java.util.UUID;

import static ru.vikulinva.customer.persistence.generated.Tables.OUTBOX;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final DSLContext dslContext;
    private final ObjectMapper objectMapper;

    public void writeAll(List<? extends DomainEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        InsertValuesStep5<?, UUID, String, String, UUID, JSONB> insert = dslContext.insertInto(OUTBOX,
                OUTBOX.AGGREGATE_ID,
                OUTBOX.AGGREGATE_TYPE,
                OUTBOX.EVENT_TYPE,
                OUTBOX.EVENT_ID,
                OUTBOX.PAYLOAD);
        for (DomainEvent event : events) {
            insert = insert.values(
                    UUID.fromString(event.getAggregateId()),
                    event.getAggregateType(),
                    event.getClass().getSimpleName(),
                    event.getId(),
                    JSONB.valueOf(serialize(event))
            );
        }
        insert.execute();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize domain event " + event.getClass().getSimpleName(), e);
        }
    }
}
