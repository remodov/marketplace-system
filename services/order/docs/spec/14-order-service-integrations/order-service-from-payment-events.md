---
type: integration
integration-type: inner
source: "[[payment-service]]"
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
  - event/payment-succeeded
  - event/payment-failed
  - event/refund-issued
---

# Payment → (Kafka) → Order Service: `PaymentSucceeded` / `PaymentFailed` / `RefundIssued`

Payment Service публикует исход платёжной операции; Order Service слушает и обновляет статус заказа.

## Контракты

### `PaymentSucceeded`

- **Топик:** `marketplace.payments.v1`
- **Headers:** `x-event-type: PaymentSucceeded`.
- **Payload:** `{ orderId, paymentId, amount, currency, gateway, paidAt }`.

### `PaymentFailed`

- **Headers:** `x-event-type: PaymentFailed`.
- **Payload:** `{ orderId, paymentId, reason: "DECLINED" | "INSUFFICIENT_FUNDS" | "GATEWAY_TIMEOUT" | "FRAUD_BLOCK", failedAt }`.

### `RefundIssued`

- **Headers:** `x-event-type: RefundIssued`.
- **Payload:** `{ orderId, refundId, amount, refundedAt }`.

### `RefundFailed`

- **Headers:** `x-event-type: RefundFailed`.
- **Payload:** `{ orderId, refundId, reason, failedAt }`.

## Обработка в Order Service

- `HandlePaymentSucceededHandler` — `Order: PENDING_PAYMENT → PAID`, эмиссия `OrderPaid`. Idempotent.
- `HandlePaymentFailedHandler` — `Order: PENDING_PAYMENT → DRAFT`, снимает `reservationId`, эмиссия `OrderPaymentFailed` → Inventory снимет резерв.
- `HandleRefundIssuedHandler` — завершает Saga `ProcessRefund`, `Order: CANCELLING/DISPUTE → REFUNDED`, эмиссия `OrderRefunded`.
- `HandleRefundFailedHandler` — Saga `→ FAILED`, оператору в Admin BFF приходит таска для ручного разбора.

## Гарантии

- At-least-once с idempotent consumer (`processed_events`, `BR-011`).
- Порядок гарантирован по `orderId` (через ключ Kafka).
- В случае задержки события (более 30 секунд после ожидаемого) — алёрт `payment-event-lag`.
