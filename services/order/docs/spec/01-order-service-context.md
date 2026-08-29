---
type: context-section
context: order-service
parent: "[[order-service]]"
section: context
tier: C
ucp-level: 3
tags:
  - context
  - bc/order
---

## 1. Bounded Context (ограниченный контекст)

**Контекст: «Оформление заказа» (Order)**

### Отвечает за

- Жизненный цикл `Order` от черновика (`DRAFT`) до закрытия (`COMPLETED`/`REFUNDED`).
- Резервирование остатка у продавцов на момент оформления.
- Запуск платежа и реакцию на его исход.
- Координацию Saga: платёж ↔ инвентарь ↔ доставка ↔ уведомления.
- Публикацию доменных событий (`OrderConfirmed`, `OrderPaid`, `OrderShipped`, …) в Kafka через Outbox.
- Обработку отмен и возвратов на стороне заказа (само возмещение — в Payment).

### Не отвечает за

- Каталог товаров и поиск (контекст «Catalog»).
- Списание и пополнение остатка (контекст «Inventory»).
- Платёжные шлюзы и комиссии (контекст «Payment»).
- Доставку через внешних логистов (контекст «Customer BFF»/внешние ребра).
- Расчёты с продавцами (внутри Payment, см. сводный кейс).
- Аутентификацию (делегируется IdP — Keycloak).

### Соседние контексты

- **Customer BFF** — основной inbound: команды покупателя (создать, оплатить, отменить, открыть спор) приходят через REST. _Тип связи_: Customer-Supplier (Order — supplier).
- **Seller / Admin BFF** — inbound для продавца (отметить отправку) и оператора (закрыть спор). _Тип связи_: Customer-Supplier.
- **Catalog Service** — outbound REST sync: проверка существования и цены товара при оформлении черновика. _Тип связи_: Conformist (Order соответствует контракту Catalog).
- **Inventory Service** — двунаправлено через Kafka: Order публикует `OrderConfirmed` → Inventory резервирует и отвечает `ItemReserved`/`ReservationFailed`. _Тип связи_: Customer-Supplier (Order — customer reservation API).
- **Payment Service** — outbound REST sync (запуск платежа) + Kafka inbound (`PaymentSucceeded`/`PaymentFailed`). _Тип связи_: Customer-Supplier.
- **Notification Service** — outbound через Kafka (подписан на `OrderConfirmed`/`OrderShipped`/`OrderRefunded`). _Тип связи_: Open Host Service со стороны Order.

### Стейкхолдеры и владелец

- **Владелец**: команда «Заказы» (Order team).
- **Зависят от нас**: команды Customer BFF, Notification, Settlement (через события).
- **От кого зависим**: Catalog (валидация товаров), Payment (исполнение платежа), Inventory (резерв и снятие).

### Диаграмма C1 — System Context

```mermaid
flowchart TB
    classDef person fill:#08427B,stroke:#073B6F,color:#fff
    classDef bc fill:#1168BD,stroke:#0B4884,color:#fff
    classDef ext fill:#999,stroke:#6b6b6b,color:#fff

    Buyer["👤 Покупатель"]:::person
    Seller["👤 Продавец"]:::person
    Admin["👤 Оператор"]:::person

    Order(("<b>Order Service</b>")):::bc
    Cat["Catalog Service"]:::bc
    Inv["Inventory Service"]:::bc
    Pay["Payment Service"]:::bc
    Notif["Notification Service"]:::bc
    Logi["📦 Логистика<br/><sub>СДЭК/Boxberry</sub>"]:::ext

    Buyer & Seller & Admin -->|REST| Order
    Order -->|sync REST| Cat
    Order -->|sync REST| Pay
    Order -.->|Kafka events| Inv
    Inv -.->|Kafka events| Order
    Pay -.->|Kafka events| Order
    Order -.->|Kafka events| Notif
    Order -->|sync REST| Logi
```
