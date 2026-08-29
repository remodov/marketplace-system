---
type: integration
integration-type: inner
source: "[[order-service]]"
target: "[[notification-service]]"
direction: outbound
protocol: kafka
sync: async
ddd-pattern:
  - published-language
tags:
  - integration
  - integration/inner
  - protocol/kafka
  - sync/async
  - ddd/published-language
  - event/order-paid
  - event/order-shipped
  - event/order-delivered
  - event/order-cancelled
  - event/order-refunded
---

# Order Service → (Kafka) → подписчики: остальные события

Это «корзина» событий Order, на которые подписаны Notification, Settlement, Read Model и другие. Топик — `marketplace.orders.v1`, схема единая.

## События

| Событие | Подписчики | Что делают |
|---|---|---|
| `OrderPaid` | Notification, Settlement, Inventory | Notification: чек покупателю + уведомление продавцу. Settlement: фиксация суммы к расчёту. Inventory: commit резерва (списание окончательное). |
| `OrderShipped` | Notification | трек-номер покупателю |
| `OrderDelivered` | Notification | запрос отзыва |
| `OrderCompleted` | Settlement | начисление выручки продавцу в текущий период |
| `OrderCancelled` | Inventory, Notification | Inventory: снять резерв (если был). Notification: подтверждение отмены. |
| `OrderExpired` | Inventory | снять резерв |
| `OrderRefunded` | Settlement, Notification | Settlement: компенсация (с баланса продавца если деньги уже выплачены). Notification: возврат подтверждён. |
| `DisputeOpened` | Notification, Admin BFF | продавцу — уведомление с дедлайном, оператору — таск в очередь |
| `DisputeResolved` | Notification | финальное уведомление |

## Контракт

Все события следуют единой схеме (см. [08-order-service-events](../08-order-service-events.md)):

```json
{
  "id": "<event uuid>",
  "occurredAt": "<iso8601>",
  "aggregateType": "Order",
  "aggregateId": "<orderId>",
  ... // type-specific payload
}
```

`x-event-type` header определяет конкретное событие.

## Гарантии

- At-least-once.
- Idempotency на стороне подписчика (`processed_events`).
- Порядок по `orderId` (через Kafka ключ).
- Версионирование схемы — через minor (совместимые) и `x-event-version`.
