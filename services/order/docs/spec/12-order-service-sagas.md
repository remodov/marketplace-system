---
type: context-section
context: order-service
parent: "[[order-service]]"
section: sagas
tier: C
tags:
  - sagas
  - bc/order
  - distributed
  - outbox
---

## 12. Saga / Process Manager

### Saga 1: Подтверждение заказа (Confirm Order)

**Цель**: Перевести заказ из `DRAFT` в `PAID` через резервирование остатка и оплату.

**Реализация**: orchestrated saga, состояние хранится в самом агрегате `Order` (`status` + `reservationId`). Координация — серия handler-ов `Order Service`.

**Шаги**:

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer (BFF)
    participant O as Order Service
    participant Inv as Inventory Service
    participant P as Payment Service
    participant K as Kafka

    C->>O: POST /orders/{id}/confirm
    O->>O: Order.confirm() (DRAFT→PENDING_PAYMENT)
    O->>K: OrderConfirmed (через Outbox)
    K-->>Inv: OrderConfirmed
    Inv->>Inv: reserve(items)
    Inv->>K: ItemReserved (или ReservationFailed)
    K-->>O: ItemReserved
    O->>O: HandleItemReserved (сохраняет reservationId)

    par
      C->>P: POST /payments {orderId, amount}
      P->>P: createPayment, обращение к шлюзу
      P->>K: PaymentSucceeded (или PaymentFailed)
      K-->>O: PaymentSucceeded
      O->>O: HandlePaymentSucceeded (PENDING_PAYMENT→PAID)
      O->>K: OrderPaid
    end
```

**Компенсации**:

| Шаг | При ошибке | Компенсация |
|---|---|---|
| 5. `reserve` | `ReservationFailed` | `Order → DRAFT`, эмиссия `OrderReservationFailed`; ничего не нужно откатывать. |
| 11. `payment` | `PaymentFailed` | `Order → DRAFT`, асинхронно публикуется `OrderPaymentFailed`, Inventory подписан → снимает резерв. |
| 11. `payment` | таймаут 15 мин | `ExpireUnpaidOrdersJob` переводит `→ EXPIRED`, событие `OrderExpired` снимает резерв. |

**Идемпотентность**: каждый event handler хранит `processed_event_id`; повторный приём `ItemReserved`/`PaymentSucceeded` игнорируется (`BR-011`).

### Saga 2: Возврат денег (Process Refund)

**Цель**: Вернуть деньги покупателю после `CancelOrder` (для оплаченного заказа) или после `ResolveDisputeForBuyer`.

**Реализация**: orchestrated saga, состояние хранится в таблице `refund_sagas (id, order_id, status, started_at, completed_at)` со статусами `STARTED`, `INVENTORY_RELEASED`, `PAYMENT_REFUNDING`, `COMPLETED`, `FAILED`.

**Шаги**:

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer / Admin
    participant O as Order Service
    participant Inv as Inventory Service
    participant P as Payment Service
    participant K as Kafka

    C->>O: cancel или resolve dispute (buyer)
    O->>O: создать RefundSaga (STARTED)
    O->>K: ReleaseReservationRequested
    K-->>Inv: ReleaseReservationRequested
    Inv->>Inv: release reserve / unreserve sold items
    Inv->>K: ReservationReleased
    K-->>O: ReservationReleased
    O->>O: RefundSaga → INVENTORY_RELEASED
    O->>K: RefundPaymentRequested
    K-->>P: RefundPaymentRequested
    P->>P: вызов шлюза для возврата
    P->>K: RefundIssued (или RefundFailed)
    K-->>O: RefundIssued
    O->>O: Order → REFUNDED, RefundSaga → COMPLETED
    O->>K: OrderRefunded
```

**Компенсации и обработка ошибок**:

| Ошибка | Реакция |
|---|---|
| `ReservationReleased` не приходит за 5 минут | retry от Outbox-relay (повторная публикация `ReleaseReservationRequested`); если 3 раза не пришёл — алёрт оператору, `RefundSaga → FAILED`, ручной разбор. |
| `RefundFailed` | `RefundSaga → FAILED`, `Order` остаётся в `CANCELLING` или `DISPUTE`, оператору в Admin BFF приходит таска. |
| Деньги уже выплачены продавцу (`BR-009`) | `Order → REFUNDED`, баланс продавца становится `−amount`, в следующем расчёте у него удержание. |

### Saga 3: Закрытие доставленных (Close Delivered)

**Не оркестрируемая saga, а scheduled job `CloseDeliveredOrdersJob`**.

- Запускается раз в день.
- `SELECT … FROM orders WHERE status = 'DELIVERED' AND delivered_at < now() - INTERVAL '14 days' FOR UPDATE SKIP LOCKED`.
- Для каждого: `Order → COMPLETED`, эмиссия `OrderCompleted`.
- Settlement подписан → начисляет выручку продавцу.

### Контракты Saga-сообщений

Все Saga-сообщения публикуются в топик `marketplace.orders.saga.v1` (отдельный от `marketplace.orders.v1`, чтобы не путать бизнес-события с операционными). Header `x-correlation-id` (= `orderId`) и `x-saga-id` обязательны.

### Стек

- Outbox-relay — Debezium + Kafka Connect, либо своя `@Scheduled` job, читающая `outbox WHERE published_at IS NULL`.
- Saga state — таблицы `refund_sagas`, без отдельного фреймворка (нет нужды в Camunda для этого объёма).
- Idempotent consumer — `processed_events`. См. [распределённые паттерны](https://vikulin-va.ru/distributed-patterns/).
