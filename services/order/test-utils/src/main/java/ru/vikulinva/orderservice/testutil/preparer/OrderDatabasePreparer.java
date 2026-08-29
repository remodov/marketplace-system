package ru.vikulinva.orderservice.testutil.preparer;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrderItemsPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;

import java.util.ArrayList;
import java.util.List;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.IDEMPOTENCY_KEYS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDER_ITEMS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.PROCESSED_EVENTS;

/**
 * Fluent setup БД для тестов Order Service. Стиль bus-tickets:
 * {@code clear*()} / {@code create*()} добавляют операции в очередь;
 * {@code prepare()} исполняет их по порядку.
 *
 * <p>Не пересоздаёт схему — только {@code DELETE} (TS-10).
 * Учитывает порядок FK (сначала позиции, потом заказы).
 */
@Component
public class OrderDatabasePreparer {

    private final DSLContext dsl;
    private final List<Runnable> preparers = new ArrayList<>();

    public OrderDatabasePreparer(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ----- clear ------------------------------------------------------------

    public OrderDatabasePreparer clearOutbox() {
        preparers.add(() -> dsl.deleteFrom(OUTBOX).execute());
        return this;
    }

    public OrderDatabasePreparer clearProcessedEvents() {
        preparers.add(() -> dsl.deleteFrom(PROCESSED_EVENTS).execute());
        return this;
    }

    public OrderDatabasePreparer clearIdempotencyKeys() {
        preparers.add(() -> dsl.deleteFrom(IDEMPOTENCY_KEYS).execute());
        return this;
    }

    public OrderDatabasePreparer clearOrderItems() {
        preparers.add(() -> dsl.deleteFrom(ORDER_ITEMS).execute());
        return this;
    }

    public OrderDatabasePreparer clearOrders() {
        preparers.add(() -> dsl.deleteFrom(ORDERS).execute());
        return this;
    }

    /** Полная очистка всех таблиц UC-1. */
    public OrderDatabasePreparer clearAll() {
        return clearOutbox()
            .clearProcessedEvents()
            .clearIdempotencyKeys()
            .clearOrderItems()
            .clearOrders();
    }

    // ----- create -----------------------------------------------------------

    public OrderDatabasePreparer createOrder(OrdersPojo order) {
        preparers.add(() -> dsl.insertInto(ORDERS)
            .set(dsl.newRecord(ORDERS, order))
            .execute());
        return this;
    }

    public OrderDatabasePreparer createOrderItem(OrderItemsPojo item) {
        preparers.add(() -> dsl.insertInto(ORDER_ITEMS)
            .set(dsl.newRecord(ORDER_ITEMS, item))
            .execute());
        return this;
    }

    // ----- run --------------------------------------------------------------

    public void prepare() {
        preparers.forEach(Runnable::run);
        preparers.clear();
    }
}
