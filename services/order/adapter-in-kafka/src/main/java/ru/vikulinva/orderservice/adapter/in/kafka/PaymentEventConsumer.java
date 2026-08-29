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
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.ProcessedEventsRepository;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.PayOrderUseCase;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.util.Objects;
import java.util.UUID;

/**
 * Консьюмер событий Payment Service. Поведение:
 *
 * <ul>
 *   <li>{@code PaymentCompleted}: достаём {@code orderId, paymentId} из payload,
 *       проверяем идемпотентность через {@link ProcessedEventsRepository},
 *       диспатчим {@link PayOrderUseCase}.</li>
 *   <li>Прочие типы — игнорируются с DEBUG-логом.</li>
 * </ul>
 *
 * <p>Метод {@code @Transactional}: PayOrder Handler работает в той же транзакции,
 * processed_events INSERT тоже в ней — атомарность обеспечена.
 */
@Component
@InboundAdapter("Payment Service Kafka consumer (PaymentCompleted)")
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final UseCaseDispatcher useCaseDispatcher;
    private final ProcessedEventsRepository processedEventsRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeService dateTimeService;

    public PaymentEventConsumer(UseCaseDispatcher useCaseDispatcher,
                                  ProcessedEventsRepository processedEventsRepository,
                                  ObjectMapper objectMapper,
                                  DateTimeService dateTimeService) {
        this.useCaseDispatcher = Objects.requireNonNull(useCaseDispatcher, "useCaseDispatcher");
        this.processedEventsRepository = Objects.requireNonNull(processedEventsRepository, "processedEventsRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Transactional
    @KafkaListener(topics = "${orderservice.kafka.payment-topic:marketplace.payments.v1}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "event-type");
        if (!"PaymentCompleted".equals(eventType)) {
            log.debug("Ignoring payment event of type {}", eventType);
            return;
        }

        UUID eventId = UUID.fromString(Objects.requireNonNull(headerValue(record, "event-id"),
            "event-id header missing"));
        if (!processedEventsRepository.markProcessed(eventId, eventType, dateTimeService.now())) {
            log.debug("PaymentCompleted #{} already processed, skipping", eventId);
            return;
        }

        try {
            JsonNode body = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(body.get("orderId").asText());
            UUID paymentId = UUID.fromString(body.get("paymentId").asText());
            useCaseDispatcher.dispatch(new PayOrderUseCase(OrderId.of(orderId), paymentId));
        } catch (Exception e) {
            log.error("Failed to process PaymentCompleted #{}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("PaymentCompleted processing failed: " + eventId, e);
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
