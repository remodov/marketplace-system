---
type: context-section
context: order-service
parent: "[[order-service]]"
section: errors
tier: C
tags:
  - errors
  - rfc-9457
  - bc/order
---

## 13. Каталог ошибок

Формат — RFC 9457 ProblemDetails (`application/problem+json`). См. [REST API: ошибки](https://vikulin-va.ru/rest-api-style-guide/oshibki/).

| Код (`code`) | HTTP | Когда возникает | Возникает в (commands/UC) | Триггерует BR |
|---|---|---|---|---|
| `ORDER_NOT_FOUND` | 404 | заказ не существует или нет доступа | `GetOrderByIdQuery`, `*` | `BR-008` |
| `ORDER_INVALID_STATE` | 409 | команда не применима к текущему статусу | `MarkShipped`, `MarkDelivered`, `CancelOrder`, `OpenDispute`, `ConfirmOrder` | переходы §4 |
| `EMPTY_ORDER` | 400 | попытка `ConfirmOrder` без позиций | `ConfirmOrder` | `BR-002` |
| `ORDER_BELOW_MINIMUM` | 400 | `total < 100 RUB` при `ConfirmOrder` | `ConfirmOrder` | `BR-013` |
| `MULTI_SELLER_NOT_SUPPORTED` | 400 | попытка добавить товар другого продавца | `CreateOrder`, `AddItem` | `BR-014` |
| `OUT_OF_STOCK` | 409 | резерв не удался (по событию `ReservationFailed`) | `ConfirmOrder` (асинхронно) | `BR-002` |
| `PRODUCT_NOT_FOUND` | 404 | Catalog не нашёл товар | `CreateOrder`, `AddItem` | — |
| `PROMO_INVALID` | 400 | промокод не существует / истёк / израсходован | `ApplyPromo` | `BR-003` |
| `PROMO_NOT_APPLICABLE` | 400 | промокод не подходит товару / категории / минимальной сумме | `ApplyPromo` | — |
| `PROMO_ALREADY_APPLIED` | 409 | заказ уже имеет применённый промокод | `ApplyPromo` | `BR-003` |
| `PAYMENT_FAILED` | 422 | платёж отклонён шлюзом (приходит как событие, отображается в UI при следующем чтении) | `HandlePaymentFailed` | `BR-011` |
| `PAYMENT_TIMEOUT` | 504 | шлюз не ответил вовремя | `HandlePayment*` | — |
| `REFUND_TOO_LATE` | 422 | прошло > 14 дней с `DELIVERED` | `OpenDispute` | `BR-007` |
| `DISPUTE_ALREADY_OPEN` | 409 | спор по этому заказу уже открыт | `OpenDispute` | — |
| `FORBIDDEN` | 403 | ABAC: нет прав на этот заказ | любая команда/query | `BR-008` |
| `UNAUTHORIZED` | 401 | JWT отсутствует или невалиден | любая | — |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | тот же `Idempotency-Key` использован для другого тела запроса | `CreateOrder` | `BR-010` |
| `INTERNAL_ERROR` | 500 | unexpected (баг или сбой инфраструктуры); инвариант не сошёлся | любая | `BR-001` (бага) |

### Структура ProblemDetails

```json
{
  "type": "https://vikulin-va.ru/errors/order-invalid-state",
  "title": "Order is not in a valid state for this action",
  "status": 409,
  "code": "ORDER_INVALID_STATE",
  "detail": "Cannot mark as shipped: order is in DRAFT, expected PAID",
  "instance": "/api/v1/orders/0fd3-…/ship",
  "violations": []
}
```

`code` — стабильный машинный идентификатор; `title` — для разработчиков; `detail` — для отладки. UI берёт текст из таблицы в [11-order-service-ui.md](11-order-service-ui.md).

### Маппинг ошибок Inventory / Payment

Ошибки приходят асинхронно через события — не как HTTP-ответы:

- `ReservationFailed` (Kafka) → Order переводит в `DRAFT`, в Read Model отображается флаг «требуется внимание», при следующем чтении BFF возвращает `OUT_OF_STOCK` в payload как deferred error.
- `PaymentFailed` (Kafka) → аналогично, deferred `PAYMENT_FAILED`.

Для синхронных вызовов (Catalog при создании) — Order пробрасывает HTTP-ошибку как есть, мапируя в свои коды.
