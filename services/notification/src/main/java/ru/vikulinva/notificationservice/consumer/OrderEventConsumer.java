package ru.vikulinva.notificationservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.service.NotificationService;

import java.util.Objects;
import java.util.UUID;

/**
 * Консьюмер событий Order Service. На вход — JSON-payload + headers с
 * метаданными ({@code event-id}, {@code event-type}). Идемпотентность
 * — внутри {@link NotificationService#processIncomingEvent}.
 *
 * <p>Достаёт {@code customerId} (для большинства событий), либо
 * {@code sellerId} для DisputeOpened (там адресат — продавец).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @KafkaListener(topics = "${notificationservice.kafka.orders-topic:marketplace.orders.v1}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "event-type");
        String eventIdHeader = headerValue(record, "event-id");
        if (eventType == null || eventIdHeader == null) {
            log.warn("Order event without required headers, skipping: type={} id={}", eventType, eventIdHeader);
            return;
        }
        UUID eventId = UUID.fromString(eventIdHeader);

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID userId = recipientFor(eventType, payload);
            if (userId == null) {
                log.debug("Cannot determine recipient for {} #{}", eventType, eventId);
                return;
            }
            notificationService.processIncomingEvent(eventId, eventType, userId, record.value());
        } catch (Exception e) {
            log.error("Failed to process {} #{}: {}", eventType, eventId, e.getMessage(), e);
            throw new IllegalStateException("Order event processing failed: " + eventId, e);
        }
    }

    /**
     * Адресат уведомления зависит от типа события:
     * — DisputeOpened уходит продавцу (sellerId);
     * — все остальные — покупателю (customerId).
     */
    private UUID recipientFor(String eventType, JsonNode payload) {
        String field = switch (eventType) {
            case "DisputeOpened" -> "sellerId";
            default -> "customerId";
        };
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) return null;
        return UUID.fromString(node.asText());
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
