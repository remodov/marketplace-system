package ru.vikulinva.orderservice.adapter.out.postgres.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.enums.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.port.out.OrderQueryPort;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.count;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDER_ITEMS;

/**
 * jOOQ-реализация {@link OrderQueryPort}. Без загрузки агрегата —
 * собирает только проекцию {@link OrderSummary}. Делает 3 запроса:
 * count, выборка страницы заказов, count позиций по найденным id.
 */
@Component
@OutboundAdapter("jOOQ-based read projections for orders")
public class JooqOrderQueryAdapter implements OrderQueryPort {

    private final DSLContext dsl;

    public JooqOrderQueryAdapter(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public PageResult<OrderSummary> listByCustomer(CustomerId customerId,
                                                    ru.vikulinva.orderservice.domain.valueobject.OrderStatus status,
                                                    int page,
                                                    int size) {
        Objects.requireNonNull(customerId, "customerId");

        Condition where = ORDERS.CUSTOMER_ID.eq(customerId.value());
        if (status != null) {
            where = where.and(ORDERS.STATUS.eq(OrderStatus.valueOf(status.name())));
        }
        return runListQuery(where, page, size);
    }

    @Override
    public PageResult<OrderSummary> listBySeller(SellerId sellerId,
                                                   ru.vikulinva.orderservice.domain.valueobject.OrderStatus status,
                                                   int page,
                                                   int size) {
        Objects.requireNonNull(sellerId, "sellerId");

        Condition where = ORDERS.SELLER_ID.eq(sellerId.value());
        if (status != null) {
            where = where.and(ORDERS.STATUS.eq(OrderStatus.valueOf(status.name())));
        }
        return runListQuery(where, page, size);
    }

    private PageResult<OrderSummary> runListQuery(Condition where, int page, int size) {

        long total = dsl.fetchCount(dsl.selectOne().from(ORDERS).where(where));
        if (total == 0) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        var rows = dsl
            .select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.SELLER_ID, ORDERS.STATUS,
                ORDERS.TOTAL_AMOUNT, ORDERS.CURRENCY, ORDERS.CREATED_AT)
            .from(ORDERS)
            .where(where)
            .orderBy(ORDERS.CREATED_AT.desc())
            .limit(size)
            .offset((long) page * size)
            .fetch();

        List<UUID> orderIds = rows.stream().map(r -> r.get(ORDERS.ID)).toList();
        Map<UUID, Integer> countByOrder = dsl
            .select(ORDER_ITEMS.ORDER_ID, count())
            .from(ORDER_ITEMS)
            .where(ORDER_ITEMS.ORDER_ID.in(orderIds))
            .groupBy(ORDER_ITEMS.ORDER_ID)
            .fetch()
            .stream()
            .collect(Collectors.toMap(
                r -> r.get(ORDER_ITEMS.ORDER_ID),
                r -> r.get(1, Integer.class)));

        List<OrderSummary> summaries = rows.stream().map(r -> {
            Currency currency = Currency.getInstance(r.get(ORDERS.CURRENCY).trim());
            Money totalAmount = new Money(r.get(ORDERS.TOTAL_AMOUNT), currency);
            int cnt = countByOrder.getOrDefault(r.get(ORDERS.ID), 0);
            return new OrderSummary(
                OrderId.of(r.get(ORDERS.ID)),
                CustomerId.of(r.get(ORDERS.CUSTOMER_ID)),
                SellerId.of(r.get(ORDERS.SELLER_ID)),
                ru.vikulinva.orderservice.domain.valueobject.OrderStatus.valueOf(r.get(ORDERS.STATUS).name()),
                totalAmount,
                cnt,
                r.get(ORDERS.CREATED_AT).toInstant());
        }).toList();

        return new PageResult<>(summaries, total, page, size);
    }

    @Override
    public List<OrderId> findExpiredPendingPayment(Instant threshold, int limit) {
        OffsetDateTime offset = OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC);
        return dsl.select(ORDERS.ID)
            .from(ORDERS)
            .where(ORDERS.STATUS.eq(OrderStatus.PENDING_PAYMENT))
            .and(ORDERS.CREATED_AT.lt(offset))
            .orderBy(ORDERS.CREATED_AT.asc())
            .limit(limit)
            .fetch(r -> OrderId.of(r.get(ORDERS.ID)));
    }

    @Override
    public List<OrderId> findStaleDelivered(Instant threshold, int limit) {
        OffsetDateTime offset = OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC);
        return dsl.select(ORDERS.ID)
            .from(ORDERS)
            .where(ORDERS.STATUS.eq(OrderStatus.DELIVERED))
            .and(ORDERS.DELIVERED_AT.lt(offset))
            .orderBy(ORDERS.DELIVERED_AT.asc())
            .limit(limit)
            .fetch(r -> OrderId.of(r.get(ORDERS.ID)));
    }
}
