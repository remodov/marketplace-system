package ru.vikulinva.orderservice.domain.repository;

import ru.vikulinva.ddd.AggregateRepository;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/**
 * Репозиторий агрегата {@link Order}. Интерфейс в домене,
 * реализация — в {@code adapter-out-postgres} (R-REP-1, R-REP-2).
 *
 * <p>Контракт {@code save}: атомарно сохраняет агрегат целиком
 * + публикует {@code DomainEvent}-ы через {@code DomainEventPublisher}
 * + вызывает {@code clearDomainEvents()} (R-REP-4).
 */
public interface OrderRepository extends AggregateRepository<Order, OrderId> {
}
