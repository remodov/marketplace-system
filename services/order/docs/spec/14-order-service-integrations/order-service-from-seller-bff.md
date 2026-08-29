---
type: integration
integration-type: inner
source: "[[seller-bff]]"
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

# Seller BFF → Order Service (REST, sync)

Seller BFF позволяет продавцу видеть свои заказы и отмечать отправку.

## Контракт

- **Endpoints:**
  - `GET /api/v1/orders?role=seller&status=…` (`SearchSellerOrders`)
  - `POST /api/v1/orders/{id}/ship` (`MarkShipped`)
  - `GET /api/v1/orders/{id}` (`GetOrderById`)
- **OpenAPI:** общий `docs/api/order-service.openapi.yaml`, scope `seller`.

## Аутентификация

JWT с ролью `seller` и `sub` = UUID продавца. ABAC: `sellerId` в позициях заказа должен совпадать с `jwt.sub` (или admin override).

## SLA

`< 200ms` для запросов; `< 1s` для `MarkShipped`.
