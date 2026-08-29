package ru.vikulinva.orderservice.adapter.out.postgres.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import ru.vikulinva.ddd.DomainEvent;

/**
 * Сериализация {@link DomainEvent} → {@link JSONB} для записи в outbox.
 *
 * <p>Мэппер собственный, а не общий бин приложения: во внешнем событии свои
 * правила — см. {@link ExternalContractModule}.
 */
@Component
public class EventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public EventPayloadSerializer(ObjectMapper objectMapper) {
        // TODO шаг 10: у внешнего события свои правила сериализации.
        // Общий ObjectMapper приложения пишет внутренние типы как есть — посмотри
        // тестом, что при этом уезжает в payload.
        this.objectMapper = objectMapper;
    }

    public JSONB toPayload(DomainEvent event) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DomainEvent " + event.getClass().getSimpleName(), e);
        }
    }
}
