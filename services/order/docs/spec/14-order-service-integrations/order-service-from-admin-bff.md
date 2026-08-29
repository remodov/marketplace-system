---
type: integration
integration-type: inner
source: "[[admin-bff]]"
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
---

# Admin BFF → Order Service (REST, sync)

Admin BFF — оператор маркетплейса разрешает споры, видит все заказы, делает ручные корректировки.

## Контракт

- **Endpoints:**
  - `GET /api/v1/orders?role=admin&…` (`SearchAllOrders`)
  - `GET /api/v1/orders/{id}` + `GetOrderTimeline`
  - `POST /api/v1/orders/{id}/disputes/resolve` (`ResolveDisputeFor*`)
  - расширенные команды (`MarkDelivered` от имени курьера, `CancelOrder` от имени пользователя в крайних случаях)

## Аутентификация

JWT с ролью `admin`. Внутри Admin BFF — обязательная MFA. Все вызовы пишутся в audit log на стороне Order Service (таблица `order_audit_log`).

## SLA

`< 500ms` для запросов; `< 2s` для разрешения спора.
