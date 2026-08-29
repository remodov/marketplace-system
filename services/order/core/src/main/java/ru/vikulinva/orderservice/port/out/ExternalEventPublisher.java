package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Порт «внешний publisher событий». Outbox-relay вычитывает строки из
 * {@code outbox} и вызывает этот порт. Реализации: Kafka, RabbitMQ, в
 * тестах/локально — логирующая. Контракт: {@code publish} либо успешно
 * доставляет сообщение во внешнюю шину, либо бросает исключение —
 * relay не пометит запись как опубликованную и попробует позже.
 */
@OutboundPort
public interface ExternalEventPublisher {

    /**
     * @param eventId       UUID записи в outbox (= eventId события)
     * @param aggregateType канонический тип агрегата (e.g. "Order")
     * @param aggregateId   id агрегата
     * @param eventType     simple-имя события (e.g. "OrderCreated")
     * @param eventVersion  версия схемы события
     * @param payload       JSON-полезная нагрузка (как хранится в outbox)
     * @param occurredAt    момент появления события в БД
     */
    void publish(UUID eventId,
                  String aggregateType,
                  UUID aggregateId,
                  String eventType,
                  int eventVersion,
                  String payload,
                  Instant occurredAt);
}
