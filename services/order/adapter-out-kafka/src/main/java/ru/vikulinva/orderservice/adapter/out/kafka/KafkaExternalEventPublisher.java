package ru.vikulinva.orderservice.adapter.out.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Kafka-реализация {@link ExternalEventPublisher}. Публикует события из
 * Outbox в один топик ({@code orderservice.kafka.events-topic}) с ключом
 * = aggregateId (для партиционирования по заказу) и заголовками с
 * метаданными события (id, type, version, occurredAt).
 *
 * <p>Когда этот бин на classpath, Logging-stub в bootstrap отключается
 * (через {@code @ConditionalOnMissingBean(name = "kafkaExternalEventPublisher")}).
 *
 * <p>Контракт {@code ExternalEventPublisher#publish}: либо успешно
 * отправляет, либо бросает — outbox-relay не пометит запись опубликованной.
 * {@code KafkaTemplate.send().get()} блокирует до подтверждения брокера —
 * это даёт нам синхронность семантики.
 */
@Component("kafkaExternalEventPublisher")
@OutboundAdapter("Kafka publisher used by Outbox-relay")
public class KafkaExternalEventPublisher implements ExternalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaExternalEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaExternalEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                        @Value("${orderservice.kafka.events-topic:marketplace.orders.v1}") String topic) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.topic = Objects.requireNonNull(topic, "topic");
    }

    @Override
    public void publish(UUID eventId,
                         String aggregateType,
                         UUID aggregateId,
                         String eventType,
                         int eventVersion,
                         String payload,
                         Instant occurredAt) {
        Message<String> message = MessageBuilder.withPayload(payload)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .setHeader(KafkaHeaders.KEY, aggregateId.toString())
            .setHeader("event-id", eventId.toString())
            .setHeader("event-type", eventType)
            .setHeader("event-version", String.valueOf(eventVersion))
            .setHeader("aggregate-type", aggregateType)
            .setHeader("aggregate-id", aggregateId.toString())
            .setHeader("occurred-at", occurredAt.toString())
            .build();

        try {
            kafkaTemplate.send(message).get();
            log.debug("Published {} #{} to {}", eventType, eventId, topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + eventType + " #" + eventId, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + eventType + " #" + eventId, e);
        }
    }
}
