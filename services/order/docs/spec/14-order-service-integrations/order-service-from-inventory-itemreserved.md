---
type: integration
integration-type: inner
source: "[[inventory-service]]"
target: "[[order-service]]"
direction: inbound
protocol: kafka
sync: async
ddd-pattern:
  - published-language
tags:
  - integration
  - integration/inner
  - protocol/kafka
  - sync/async
  - event/item-reserved
  - event/reservation-failed
---

# Inventory → (Kafka) → Order Service: `ItemReserved` / `ReservationFailed`

Inventory отвечает на `OrderConfirmed` событием успеха или отказа резерва.

## Контракты

### `ItemReserved`

- **Топик:** `marketplace.inventory.v1`
- **Headers:** `x-event-type: ItemReserved`, `x-correlation-id: <orderId>`.
- **Payload:** `{ orderId, reservationId, reservedAt }`.

### `ReservationFailed`

- **Топик:** `marketplace.inventory.v1`
- **Headers:** `x-event-type: ReservationFailed`.
- **Payload:** `{ orderId, reason: "OUT_OF_STOCK" | "PRODUCT_UNAVAILABLE", failedItems: [<productId>] }`.

## Обработка в Order Service

- `HandleItemReservedHandler` — записывает `reservationId` в агрегат, заказ остаётся в `PENDING_PAYMENT`. Idempotent (`processed_events`).
- `HandleReservationFailedHandler` — переводит `Order → DRAFT`, эмиссия `OrderReservationFailed`, в Read Model появляется флаг для UI.

## Гарантии

- At-least-once с idempotent consumer.
- Если `ItemReserved` не приходит за 5 секунд — Order продолжает ждать; через 30 секунд логирует warning, через 5 минут — алёрт. Заказ «застревает» в `PENDING_PAYMENT` без `reservationId`. Восстановление — ручное либо через retry от Outbox-relay Inventory.
