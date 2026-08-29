---
type: integration
integration-type: inner
source: "[[order-service]]"
target: "[[inventory-service]]"
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
  - event/order-confirmed
---

# Order Service → (Kafka) → Inventory Service: `OrderConfirmed`

Order Service публикует `OrderConfirmed` после `ConfirmOrderUseCase`. Inventory подписан, выполняет резервирование остатка.

## Контракт

- **Топик:** `marketplace.orders.v1`
- **Ключ:** `orderId` (для упорядочивания событий по одному заказу).
- **Headers:** `x-event-version: 1`, `x-event-type: OrderConfirmed`, `x-correlation-id: <orderId>`.
- **Payload (JSON):**
  ```json
  {
    "id": "<event uuid>",
    "occurredAt": "<iso8601>",
    "aggregateType": "Order",
    "aggregateId": "<orderId>",
    "customerId": "<uuid>",
    "sellerId": "<uuid>",
    "items": [{"productId": "<uuid>", "quantity": <int>, "unitPrice": "<decimal>"}],
    "total": {"amount": "<decimal>", "currency": "RUB"}
  }
  ```
- **DDD-паттерн:** Published Language — Order публикует контракт, Inventory его потребляет.

## Доставка

- **Гарантия:** at-least-once (Outbox-relay + Idempotent Consumer на стороне Inventory).
- **Порядок:** по `orderId` (внутри партиции Kafka).
- **Версионирование:** изменения совместимые → `x-event-version` без изменений; breaking — новый топик `marketplace.orders.v2`.

## Подписчики

- **Inventory Service** — резервирует остаток.
- **Notification Service** — отправляет «заказ подтверждён».
- **Read Model `order_summaries`** (внутри Order Service) — обновляется через `@TransactionalEventListener(AFTER_COMMIT)`.

## Ответ

Inventory отвечает событием `ItemReserved` или `ReservationFailed` (см. `order-service-from-inventory-itemreserved.md`).
