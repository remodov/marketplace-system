---
type: context-section
context: order-service
parent: "[[order-service]]"
section: model
tier: C
tags:
  - model
  - bc/order
  - aggregate/order
---

## 3. Domain Model

### 3.1 Агрегаты

**Агрегат `Order`** — корень. Защищает инварианты: согласованность суммы, валидность переходов состояний, отсутствие дублирования позиций по `(productId, sellerId)`.

| Атрибут | Тип | Описание |
|---|---|---|
| id | `OrderId` (UUID) | первичный ключ |
| customerId | `CustomerId` (UUID) | FK на покупателя (по ID) |
| status | `OrderStatus` (enum) | текущая фаза жизненного цикла |
| items | `List<OrderItem>` | позиции заказа, ≥ 1 для перехода в `PENDING_PAYMENT` |
| discount | `Discount?` | применённая скидка (от промокода или акции) |
| shippingFee | `Money` | стоимость доставки |
| total | `Money` (вычисляется) | сумма позиций − скидка + доставка |
| reservationId | `ReservationId?` | id внешнего резерва в Inventory; null до `PENDING_PAYMENT` |
| paymentId | `PaymentId?` | id последней успешной попытки платежа |
| paidAt / shippedAt / deliveredAt / closedAt | `Instant?` | временные метки переходов |
| createdAt / updatedAt | `Instant` | служебные |
| events | `List<DomainEvent>` | накопленные события (clear после публикации) |

### 3.2 Сущности

**`OrderItem`** — внутренняя сущность. Уникальна в рамках агрегата по `(productId, sellerId)`. Не существует вне `Order`. Содержит: `productId`, `sellerId`, `quantity` (`Quantity` VO), `unitPrice` (`Money` VO), `lineTotal` (вычисляемое).

### 3.3 Value Objects

- **`OrderId`**, **`OrderItemId`**, **`CustomerId`**, **`SellerId`**, **`ProductId`** — типизированные обёртки над UUID. `equals` по значению.
- **`Money`** — `BigDecimal amount` + `Currency currency` (всегда RUB в этой версии). Immutable. Операции `add`/`subtract`/`multiply` возвращают новый `Money`. Не допускает отрицательных сумм.
- **`Quantity`** — целое число от 1 до 999. Защищает от отрицательных и нулевых количеств.
- **`Discount`** — sealed: `PercentageDiscount(BigDecimal pct)` либо `FixedDiscount(Money amount)`. Применяется к `OrderTotal`.
- **`OrderStatus`** — enum: `DRAFT`, `PENDING_PAYMENT`, `PAID`, `SHIPPED`, `DELIVERED`, `COMPLETED`, `EXPIRED`, `CANCELLED`, `REFUNDED`, `DISPUTE`.
- **`Address`** — адрес доставки: страна, город, улица, индекс, ПВЗ-код (если применимо).

### Диаграмма C3 — Domain Model

```mermaid
classDiagram
    class Order {
        <<Aggregate Root>>
        id: OrderId
        customerId: CustomerId
        status: OrderStatus
        discount: Discount?
        shippingFee: Money
        reservationId: ReservationId?
        paymentId: PaymentId?
        +addItem(productId, sellerId, qty, price)
        +applyPromo(promo)
        +confirm() : DomainEvent
        +pay(paymentId) : DomainEvent
        +ship(shipmentRef) : DomainEvent
        +deliver() : DomainEvent
        +close()
        +cancel() : DomainEvent
        +openDispute(reason) : DomainEvent
    }
    class OrderItem {
        <<Entity>>
        id: OrderItemId
        productId: ProductId
        sellerId: SellerId
        quantity: Quantity
        unitPrice: Money
        +lineTotal() : Money
    }
    class Money {
        <<Value Object>>
        amount: BigDecimal
        currency: Currency
    }
    class Quantity {
        <<Value Object>>
        value: int
    }
    class Discount {
        <<sealed>>
    }
    class OrderStatus {
        <<Enum>>
    }

    Order "1" *-- "1..*" OrderItem
    Order --> OrderStatus
    Order --> Discount
    OrderItem --> Money
    OrderItem --> Quantity
```

### 3.4 Доменные события

Список — в [08-order-service-events](08-order-service-events.md). Публикуются из агрегата через `registerEvent(...)` и доставляются репозиторием в `Outbox` в той же транзакции с `save`.

### 3.5 Схема базы данных

```mermaid
erDiagram
    orders {
        uuid id PK
        uuid customer_id "FK на покупателя (логически)"
        order_status status "enum"
        money_total numeric_total "сумма для read-side"
        currency currency
        uuid reservation_id "FK на Inventory (по ID)"
        uuid payment_id "FK на Payment (по ID)"
        money discount_amount "nullable"
        money shipping_fee
        timestamp created_at
        timestamp updated_at
        timestamp paid_at "nullable"
        timestamp shipped_at "nullable"
        timestamp delivered_at "nullable"
        timestamp closed_at "nullable"
    }
    order_items {
        uuid id PK
        uuid order_id FK
        uuid product_id "FK на Catalog (логически)"
        uuid seller_id "FK на продавца (логически)"
        integer quantity
        money unit_price
    }
    outbox {
        uuid id PK
        uuid aggregate_id "= orders.id"
        varchar aggregate_type "Order"
        varchar event_type "OrderConfirmed | OrderPaid | …"
        jsonb payload
        timestamp occurred_at
        timestamp published_at "nullable, null до публикации"
    }

    orders ||--o{ order_items : contains
    orders ||--o{ outbox : produces
```

Индексы:
- `idx_orders_customer_status (customer_id, status)` — UC-5 (заказы покупателя).
- `idx_orders_seller_status (seller_id, status)` через JOIN с `order_items` (или materialized view) — UC-6 (заказы продавца).
- `idx_outbox_unpublished (occurred_at) WHERE published_at IS NULL` — Outbox-relay.
