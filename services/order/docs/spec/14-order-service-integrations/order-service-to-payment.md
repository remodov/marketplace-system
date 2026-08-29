---
type: integration
integration-type: inner
source: "[[order-service]]"
target: "[[payment-service]]"
direction: outbound
protocol: rest
sync: sync
ddd-pattern:
  - customer-supplier
tags:
  - integration
  - integration/inner
  - protocol/rest
  - sync/sync
  - ddd/customer-supplier
---

# Order Service → Payment Service (REST, sync — старт; Kafka inbound — исход)

Order инициирует платёж синхронным REST-вызовом, исход получает асинхронно через Kafka. См. также «Order Service ← Payment Service (Kafka)».

## Контракт

- **Endpoint (Payment):** `POST /api/v1/payments`
- **Тело:** `{ orderId, amount, currency: "RUB", customerId, idempotencyKey }`
- **Ответ:** `201 Created` с `{ paymentId, paymentUrl }`. `paymentUrl` — куда BFF делает редирект пользователя.
- **OpenAPI:** `payment-service/docs/api/payment.openapi.yaml`.
- **DDD-паттерн:** Customer-Supplier. Payment — supplier; Order — customer.

## Аутентификация

mTLS + JWT `system` с scope `payment:initiate`.

## Resilience

- **Timeout:** 3 секунды (включая первичную регистрацию у банка).
- **Retry:** 3 попытки с jitter 100–500ms.
- **Circuit Breaker:** Resilience4j; sliding window 60s, threshold 50%.
- **Idempotency-Key:** обязателен; повтор с тем же ключом возвращает прежний `paymentId`.

## SLA

`< 2s p95`. При недоступности — Order возвращает `PAYMENT_TIMEOUT` (504), покупателю показывается «попробуйте через минуту»; идемпотентность защищает от двойного списания.
