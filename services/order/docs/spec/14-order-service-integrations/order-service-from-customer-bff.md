---
type: integration
integration-type: inner
source: "[[customer-bff]]"
target: "[[order-service]]"
direction: inbound
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

# Customer BFF → Order Service (REST, sync)

Customer BFF проксирует команды покупателя: создание заказа, подтверждение, оплата, отмена, открытие спора.

## Контракт

- **Endpoints:**
  - `POST /api/v1/orders` (`CreateOrder`)
  - `POST /api/v1/orders/{id}/confirm` (`ConfirmOrder`)
  - `POST /api/v1/orders/{id}/cancel` (`CancelOrder`)
  - `POST /api/v1/orders/{id}/disputes` (`OpenDispute`)
  - `GET /api/v1/orders/{id}` (`GetOrderById`)
  - `GET /api/v1/orders` (`SearchMyOrders`)
- **OpenAPI:** `docs/api/order-service.openapi.yaml`
- **DDD-паттерн:** Customer-Supplier. Order — supplier (владеет моделью). BFF — customer.

## Аутентификация

JWT от Keycloak (Authorization Code + PKCE flow в BFF). Order Service валидирует JWT через JWK Set. В JWT обязательны:

- `sub` — UUID покупателя (используется как `customerId` в ABAC).
- `realm_access.roles` содержит `customer`.

## Идемпотентность

`POST /api/v1/orders` требует заголовок `Idempotency-Key` (UUID). См. `BR-010`.

## SLA

Ответ `< 1.5s` для синхронных команд (`Create`, `Confirm`, `Cancel`); `< 200ms` для запросов (через Read Model).

## При недоступности

BFF получает 503 → возвращает покупателю «временно недоступно, попробуйте через минуту». Идемпотентность защищает от дублей при retry.
