---
type: context-section
context: order-service
parent: "[[order-service]]"
section: events
tier: C
tags:
  - events
  - bc/order
  - kafka
---

## 8. Domain Events

Все события — `final class extends DomainEvent` (`ddd-building-blocks`). Регистрируются в агрегате `Order` через `registerEvent(...)` и публикуются через `Outbox` в той же транзакции.

### Внутренние и внешние

- **Внутренние** (in-process listeners) обрабатываются Spring через `@TransactionalEventListener(AFTER_COMMIT)` — например, обновление денормализованных Read Model в той же БД.
- **Внешние** публикуются в Kafka через `Outbox-relay`. Это контракт с другими сервисами.

### Каталог

| Событие | Триггер | Тип | Топик Kafka | Подписчики |
|---|---|---|---|---|
| `OrderCreated` | после `CreateOrder` | внутреннее | — | Order Read Model (для `SearchMyOrders`) |
| `OrderConfirmed` | после `ConfirmOrder` | внешнее | `marketplace.orders.v1` | Inventory (резервирует), Notification (welcome SMS) |
| `OrderReservationFailed` | после `HandleReservationFailed` | внешнее | `marketplace.orders.v1` | Notification (сообщение покупателю) |
| `OrderPaid` | после `HandlePaymentSucceeded` | внешнее | `marketplace.orders.v1` | Notification (чек), Inventory (commit резерва), Settlement (учёт) |
| `OrderShipped` | после `MarkShipped` | внешнее | `marketplace.orders.v1` | Notification (трек-номер) |
| `OrderDelivered` | после `MarkDelivered` | внешнее | `marketplace.orders.v1` | Notification (запрос отзыва), внутренний таймер 14 дней |
| `OrderCompleted` | после `CloseDeliveredOrdersJob` | внешнее | `marketplace.orders.v1` | Settlement (выручка) |
| `OrderCancelled` | после `CancelOrder` | внешнее | `marketplace.orders.v1` | Inventory (снять резерв), Notification |
| `OrderExpired` | после `ExpireUnpaidOrdersJob` | внешнее | `marketplace.orders.v1` | Inventory (снять резерв) |
| `DisputeOpened` | после `OpenDispute` | внешнее | `marketplace.orders.v1` | Notification (продавцу), Admin BFF (в очередь споров) |
| `DisputeResolved` | после `ResolveDispute` | внешнее | `marketplace.orders.v1` | Notification |
| `OrderRefunded` | после Saga `ProcessRefund` | внешнее | `marketplace.orders.v1` | Settlement (компенсация), Notification |

### Структура события

Все события наследуют `DomainEvent` и имеют:

- `id: UUID` — id события, генерируется при создании;
- `occurredAt: Instant` — время возникновения;
- `aggregateType: "Order"`;
- `aggregateId: String` — `orderId`.

Плюс типобезопасный payload, специфичный для события.

### Пример: `OrderConfirmed`

```java
public final class OrderConfirmed extends DomainEvent {
    private final List<ItemSnapshot> items;
    private final Money total;
    private final UUID customerId;
    private final UUID sellerId;

    public OrderConfirmed(UUID orderId, UUID customerId, UUID sellerId,
                          List<ItemSnapshot> items, Money total) {
        super("Order", orderId.toString());
        this.items = List.copyOf(items);
        this.total = total;
        this.customerId = customerId;
        this.sellerId = sellerId;
    }
    // getters
}
```

### Пример: `OrderPaid`

```java
public final class OrderPaid extends DomainEvent {
    private final UUID paymentId;
    private final Money amount;
    private final Instant paidAt;
    // …
}
```

### Контракты для внешних потребителей

Сериализация — JSON, схема версионирована: `marketplace.orders.v1`. Изменения payload — через minor (добавление optional полей) или новую версию топика (breaking changes). Версия указана в Kafka header `x-event-version`.

См. интеграции: `14-order-service-integrations/order-service-publishes-orderconfirmed.md`, `…-orderpaid.md`, и т.д. — там точные контракты для каждого события.

### Идемпотентность приёма

При приёме событий извне (`PaymentSucceeded`, `ItemReserved`) Order Service использует таблицу `processed_events (event_id PK, processed_at)` и не обрабатывает дубликаты повторно. Реализуется в каждом event handler (`BR-011`).
