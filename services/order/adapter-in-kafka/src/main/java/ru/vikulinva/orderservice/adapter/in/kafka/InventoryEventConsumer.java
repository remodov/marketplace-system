package ru.vikulinva.orderservice.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundAdapter;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.ProcessedEventsRepository;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.CancelOrderUseCase;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.util.Objects;
import java.util.UUID;

/**
 * Консьюмер событий Inventory Service.
 *
 * <ul>
 *   <li>{@code ReservationFailed}: товар закончился между подтверждением и
 *       резервированием — отменяем заказ системной причиной
 *       {@code RESERVATION_FAILED}. Покупатель видит через статус CANCELLED.</li>
 *   <li>{@code ItemReserved}: успешный резерв, мы ждём оплаты — no-op.</li>
 * </ul>
 */
@Component
@InboundAdapter("Inventory Service Kafka consumer")
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final UseCaseDispatcher useCaseDispatcher;
    private final ProcessedEventsRepository processedEventsRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeService dateTimeService;

    public InventoryEventConsumer(UseCaseDispatcher useCaseDispatcher,
                                    ProcessedEventsRepository processedEventsRepository,
                                    ObjectMapper objectMapper,
                                    DateTimeService dateTimeService) {
        this.useCaseDispatcher = Objects.requireNonNull(useCaseDispatcher, "useCaseDispatcher");
        this.processedEventsRepository = Objects.requireNonNull(processedEventsRepository, "processedEventsRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Transactional
    @KafkaListener(topics = "${orderservice.kafka.inventory-topic:marketplace.inventory.v1}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "event-type");
        if (eventType == null) {
            log.warn("Inventory event without event-type header, skipping");
            return;
        }

        switch (eventType) {
            case "ReservationFailed" -> handleReservationFailed(record);
            case "ItemReserved" -> log.debug("ItemReserved received, no-op (waiting for payment)");
            default -> log.debug("Ignoring inventory event of type {}", eventType);
        }
    }

    private void handleReservationFailed(ConsumerRecord<String, String> record) {
        UUID eventId = UUID.fromString(Objects.requireNonNull(headerValue(record, "event-id"),
            "event-id header missing"));
        if (!processedEventsRepository.markProcessed(eventId, "ReservationFailed", dateTimeService.now())) {
            log.debug("ReservationFailed #{} already processed, skipping", eventId);
            return;
        }

        try {
            JsonNode body = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(body.get("orderId").asText());
            UUID customerId = UUID.fromString(body.get("customerId").asText());
            useCaseDispatcher.dispatch(new CancelOrderUseCase(
                OrderId.of(orderId),
                CustomerId.of(customerId),
                CancellationReason.of("RESERVATION_FAILED")));
        } catch (Exception e) {
            log.error("Failed to process ReservationFailed #{}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("ReservationFailed processing failed: " + eventId, e);
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
