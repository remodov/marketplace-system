---
type: context-section
context: order-service
parent: "[[order-service]]"
section: acceptance
tier: C
tags:
  - acceptance
  - bc/order
  - testing
---

## 15. Критерии приёмки (Acceptance Criteria)

### Use Case 1 — Покупка (счастливый сценарий)

- [ ] Покупатель может создать заказ с одним продавцом и до 20 позиций.
- [ ] При повторном `POST /orders` с тем же `Idempotency-Key` возвращается тот же `orderId` (проверяется в integration-тесте).
- [ ] Сумма заказа = `sum(items) − discount + shippingFee` (`BR-001`).
- [ ] После `ConfirmOrder` заказ находится в `PENDING_PAYMENT` и `OrderConfirmed` есть в Outbox.
- [ ] Через ≤ 5 секунд после `PaymentSucceeded` заказ переходит в `PAID` и `OrderPaid` есть в Outbox.
- [ ] После `MarkShipped` заказ в `SHIPPED`.
- [ ] Через 14 дней после `MarkDelivered` без `OpenDispute` заказ переходит в `COMPLETED`.

### Use Case 2 — Отмена до отправки

- [ ] Покупатель может отменить заказ в `PAID`.
- [ ] Saga `ProcessRefund` отрабатывает за ≤ 30 секунд (e2e-тест с моками Inventory и Payment).
- [ ] `OrderRefunded` публикуется только после `RefundIssued` от Payment.

### Use Case 3 — Спор

- [ ] Покупатель может открыть спор только в `DELIVERED` и в окне 14 дней (`BR-007`).
- [ ] После `OpenDispute` продавец получает уведомление в течение 1 минуты (e2e на Notification моке).
- [ ] Решение оператора корректно переводит в `REFUNDED` или `COMPLETED`.

### Use Case 4 — Продавец отмечает отправку

- [ ] Продавец видит только заказы со своими товарами (`BR-008`, ABAC-тест).
- [ ] `MarkShipped` запрещена другому продавцу (тест возвращает `403 FORBIDDEN`).

### Use Case 5–6 — Поиск заказов

- [ ] `SearchMyOrdersQuery` фильтрует только по `customer_id == jwt.sub`.
- [ ] `SearchSellerOrdersQuery` использует Read Model и кэш на 30 секунд.
- [ ] Read Model отстаёт от write-side не более чем на 1 секунду (perf-тест).

### Бизнес-правила

| BR | Тип теста | Покрытие |
|---|---|---|
| BR-001 | unit (Order aggregate) | пересчёт `total` при `addItem`/`removePromo` |
| BR-002 | integration (Saga) | `ConfirmOrder` ждёт `ItemReserved`; `ReservationFailed` возвращает в `DRAFT` |
| BR-003 | unit | повторный `applyPromo` падает |
| BR-007 | unit + integration | `OpenDispute` на `DELIVERED + 15 дней` → `REFUND_TOO_LATE` |
| BR-008 | integration (ABAC) | покупатель не видит чужой заказ; продавец не видит чужой |
| BR-010 | integration | повторный `Idempotency-Key` возвращает прежний orderId |
| BR-011 | integration | повторный `PaymentSucceeded` не ломает состояние |
| BR-013 | unit | `total < 100 RUB` блокирует `ConfirmOrder` |

### Покрытие тестами

- **Unit (`Order` aggregate, `Money`, `Quantity`)** — 100% веток инвариантов.
- **Integration (`*UseCaseHandler` + Testcontainers `postgres` + `kafka`)** — все commands и event handlers.
- **E2E (`Customer BFF` mock → Order → `Inventory`/`Payment` mocks)** — UC-1 .. UC-7 каждый.
- **Performance (Gatling)** — 200 RPS на `CreateOrder`, p95 < 1.5s.

### Вход в прод

- [ ] Feature flag `order-v3` для постепенного раскатывания (по % покупателей).
- [ ] Дашборды Grafana: `orders_created_total`, `orders_paid_total`, `outbox_lag_seconds`, `processed_events_duplicates_total`.
- [ ] Алёрты: `orders_paid_lag_5m > 30s`, `outbox_unpublished_count > 100`.
- [ ] Канарей: 5% трафика → 50% → 100% за 7 дней.
