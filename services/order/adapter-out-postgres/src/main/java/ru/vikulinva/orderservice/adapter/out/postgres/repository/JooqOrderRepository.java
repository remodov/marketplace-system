package ru.vikulinva.orderservice.adapter.out.postgres.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.ddd.DomainEventPublisher;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrderItemsPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.mapper.OrderJooqMapper;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

import java.util.List;
import java.util.Optional;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDER_ITEMS;

/**
 * Реализация {@link OrderRepository} на jOOQ.
 *
 * <p>Контракт {@code save}:
 * <ol>
 *   <li>Insert/upsert orders + bulk insert order_items.</li>
 *   <li>Публикация всех {@code DomainEvent}-ов через {@link OutboxDomainEventPublisher}
 *       (запись в таблицу {@code outbox} в той же транзакции).</li>
 *   <li>{@code clearDomainEvents()} на агрегате.</li>
 * </ol>
 *
 * Транзакция охватывается @{@code @Transactional} в UseCaseHandler.
 */
@Component
@OutboundAdapter("jOOQ-based Order repository with Outbox")
public class JooqOrderRepository implements OrderRepository {

    private final DSLContext dsl;
    private final OrderJooqMapper mapper;
    private final DomainEventPublisher domainEventPublisher;

    public JooqOrderRepository(DSLContext dsl,
                                OrderJooqMapper mapper,
                                DomainEventPublisher domainEventPublisher) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public Order save(Order order) {
        // Один заказ — одна транзакция; вызывается из @Transactional Handler-а.
        OrdersPojo orderPojo = mapper.toPojo(order);
        dsl.insertInto(ORDERS)
            .set(dsl.newRecord(ORDERS, orderPojo))
            .onConflict(ORDERS.ID)
            .doUpdate()
            .set(dsl.newRecord(ORDERS, orderPojo))
            .execute();

        List<OrderItemsPojo> itemPojos = mapper.toItemsPojo(order);
        // Чистим прошлые позиции, вставляем новые (для UC-1 заказ только что создан, чисто).
        dsl.deleteFrom(ORDER_ITEMS)
            .where(ORDER_ITEMS.ORDER_ID.eq(order.getId().value()))
            .execute();
        for (OrderItemsPojo itemPojo : itemPojos) {
            dsl.insertInto(ORDER_ITEMS)
                .set(dsl.newRecord(ORDER_ITEMS, itemPojo))
                .execute();
        }

        // Публикуем накопленные события через Outbox + сбрасываем (BR-015, R-REP-4).
        domainEventPublisher.publishAll(order.getEvents());
        order.clearDomainEvents();
        return order;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        OrdersPojo orderPojo = dsl.selectFrom(ORDERS)
            .where(ORDERS.ID.eq(id.value()))
            .fetchOneInto(OrdersPojo.class);
        if (orderPojo == null) {
            return Optional.empty();
        }
        List<OrderItemsPojo> itemPojos = dsl.selectFrom(ORDER_ITEMS)
            .where(ORDER_ITEMS.ORDER_ID.eq(id.value()))
            .fetchInto(OrderItemsPojo.class);
        return Optional.of(mapper.toDomain(orderPojo, itemPojos));
    }

    @Override
    public void delete(Order order) {
        dsl.deleteFrom(ORDERS).where(ORDERS.ID.eq(order.getId().value())).execute();
    }
}
