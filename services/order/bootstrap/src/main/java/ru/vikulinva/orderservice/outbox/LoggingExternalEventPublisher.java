package ru.vikulinva.orderservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;

import java.time.Instant;
import java.util.UUID;

/**
 * Заглушка {@link ExternalEventPublisher}: пишет событие в лог. Используется,
 * пока Kafka-адаптер не подключён (V0). При появлении Kafka-publisher
 * этот бин заменяется тем, что в adapter-out-kafka.
 */
@Component
@ConditionalOnMissingBean(name = "kafkaExternalEventPublisher")
@OutboundAdapter("Logs outbox events instead of publishing — V0 stub for ExternalEventPublisher")
public class LoggingExternalEventPublisher implements ExternalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingExternalEventPublisher.class);

    @Override
    public void publish(UUID eventId,
                         String aggregateType,
                         UUID aggregateId,
                         String eventType,
                         int eventVersion,
                         String payload,
                         Instant occurredAt) {
        log.info("[outbox] publish {}#{} v{} aggregate={}({}) occurredAt={} payload={}",
            eventType, eventId, eventVersion, aggregateType, aggregateId, occurredAt, payload);
    }
}
