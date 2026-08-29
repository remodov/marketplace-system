---
type: context-section
context: order-service
parent: "[[order-service]]"
section: roles
tier: C
tags:
  - roles
  - rbac
  - abac
  - bc/order
---

## 5. Роли и права доступа

### Роли (из JWT)

| Роль | Откуда | Описание |
|---|---|---|
| `customer` | Customer BFF (Authorization Code + PKCE) | покупатель |
| `seller` | Seller BFF | продавец |
| `admin` | Admin BFF (SSO + MFA) | оператор маркетплейса |
| `system` | service-to-service (client credentials / mTLS) | внутренние сервисы (Payment, Inventory) |

### Матрица команд

| Команда / Query | `customer` | `seller` | `admin` | `system` | ABAC |
|---|---|---|---|---|---|
| `CreateOrder` | ✅ | — | ✅ (от имени) | — | `customerId == jwt.sub` (кроме admin) |
| `AddItem` / `RemoveItem` | ✅ | — | ✅ | — | владение заказом |
| `ApplyPromo` | ✅ | — | — | — | владение заказом |
| `ConfirmOrder` | ✅ | — | ✅ | — | владение заказом |
| `CancelOrder` | ✅ | — | ✅ | — | владение заказом |
| `MarkShipped` | — | ✅ | ✅ | — | заказ содержит товар продавца |
| `MarkDelivered` | — | — | ✅ | ✅ | system role for courier integration |
| `OpenDispute` | ✅ | — | ✅ | — | владение заказом |
| `ResolveDispute` | — | — | ✅ | — | — |
| `GetOrderById` | ✅ | ✅ | ✅ | ✅ | покупатель — свой; продавец — содержит его товар; admin — любой |
| `SearchMyOrders` | ✅ | ✅ | ✅ | — | свои; для admin — любые |
| `PaymentSucceeded` (внутренний обработчик) | — | — | — | ✅ | — |

### ABAC-правила (агрегатные)

- **`customer` владеет заказом** ⇔ `order.customerId == jwt.sub`. Проверяется в `OrderQueryHandler` и в командных handler-ах перед вызовом метода агрегата.
- **`seller` имеет доступ к заказу** ⇔ хотя бы одна `OrderItem.sellerId == jwt.sub`. Реализуется JOIN с `order_items` в Read Model.
- **`admin` имеет полный доступ** к любому заказу.
- **`system` (внутренние сервисы)** обращается только к подмножеству команд через service-to-service: `MarkDelivered` (от логистики), приём событий `PaymentSucceeded`/`PaymentFailed`/`ItemReserved`/`ReservationFailed` (через Kafka).

### PII

В заказе хранится:

- `customerId` (UUID, не PII по 152-ФЗ — нужен mapping в User-сервисе для разрешения).
- адрес доставки (`Address`) — **PII**, шифрование at-rest.
- телефон / email — **в заказе не храним**, тянем из User-сервиса при необходимости.

См. также `16-order-service-nfr.md` (раздел Security/Compliance) и `13-order-service-errors.md` (`FORBIDDEN`, `UNAUTHORIZED`).
