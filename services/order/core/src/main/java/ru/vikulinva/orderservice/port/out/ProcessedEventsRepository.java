package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Журнал уже обработанных входящих событий — для идемпотентных консьюмеров.
 * Реализация — jOOQ over {@code processed_events} (PK event_id).
 */
@OutboundPort
public interface ProcessedEventsRepository {

    /**
     * Помечает событие обработанным. Если запись уже была — возвращает {@code false}
     * (повторная доставка) и обработку повторять не надо.
     *
     * @return {@code true}, если запись только что вставлена; {@code false} — если уже была
     */
    boolean markProcessed(UUID eventId, String eventType, Instant processedAt);
}
