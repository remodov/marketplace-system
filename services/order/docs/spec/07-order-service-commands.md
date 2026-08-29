---
type: context-section
context: order-service
parent: "[[order-service]]"
section: commands
tier: C
tags:
  - commands
  - bc/order
  - usecase/command
---

## 7. Commands

Каждая команда реализуется как `UseCaseCommand<R>` из `usecase-pattern` и обрабатывается соответствующим `UseCaseHandler` с `@Transactional`.

### `CreateOrder`

- **Класс**: `CreateOrderUseCase implements UseCaseCommand<OrderJsonBean>`
- **Инициатор**: покупатель (через Customer BFF) или оператор (admin).
- **Роль**: `customer` или `admin` (см. §5).
- **Параметры**: `customerId` (из JWT), `items` (`List<{productId, sellerId, quantity}>`), `shippingAddress`, `idempotencyKey`.
- **Агрегат**: `Order` (создаётся новый).
- **Проверки**: `BR-008` (ABAC), `BR-010` (идемпотентность), `BR-014` (один продавец).
- **Действия**:
  1. Проверить `idempotency_keys` — если ключ уже есть, вернуть прежний `orderId`.
  2. *(sync REST → Catalog)* Запросить актуальные цены и существование товаров.
  3. Создать новый `Order` в статусе `DRAFT`, рассчитать `total` (`BR-001`).
  4. Записать `Order` + `OrderCreated` в Outbox в одной транзакции.
- **Результат**: `OrderJsonBean { orderId, status: DRAFT, total }`.
- **Переход**: → `DRAFT`.
- **Ошибки**: `PRODUCT_NOT_FOUND`, `MULTI_SELLER_NOT_SUPPORTED`.

### `AddItem` / `RemoveItem`

- **Класс**: `AddItemUseCase`, `RemoveItemUseCase` (оба `UseCaseCommand<EmptyResult>`).
- **Инициатор**: владелец заказа (`customer`/`admin`).
- **Параметры**: `orderId`, `productId`, `quantity`.
- **Проверки**: `BR-008`, статус заказа = `DRAFT` иначе `ORDER_INVALID_STATE`.
- **Действия**: загрузить агрегат → `order.addItem(...)`/`order.removeItem(...)` → пересчёт `total` → save + Outbox.
- **Переход**: остаётся в `DRAFT`.

### `ApplyPromo` / `RemovePromo`

- **Класс**: `ApplyPromoUseCase`, `RemovePromoUseCase`.
- **Параметры**: `orderId`, `promoCode`.
- **Проверки**: `BR-003` (один промокод), `BR-012` (неотрицательный total).
- **Действия**: вызов Catalog для валидации промокода → `order.applyPromo(discount)` → пересчёт.
- **Ошибки**: `PROMO_INVALID`, `PROMO_NOT_APPLICABLE`, `PROMO_ALREADY_APPLIED`.

### `ConfirmOrder`

- **Класс**: `ConfirmOrderUseCase implements UseCaseCommand<OrderJsonBean>`.
- **Инициатор**: владелец заказа.
- **Параметры**: `orderId`.
- **Проверки**: статус = `DRAFT`, ≥ 1 позиции (`BR-002`), `total >= 100` (`BR-013`).
- **Действия**:
  1. Загрузить агрегат, валидировать инварианты.
  2. *(одна транзакция)* `order.confirm()` → переход в `PENDING_PAYMENT` (sub-state «ожидание резерва»), регистрация события `OrderConfirmed`, save + Outbox.
  3. Outbox-relay публикует `OrderConfirmed` в Kafka → подписан Inventory.
  4. Inventory отвечает `ItemReserved` или `ReservationFailed` (асинхронный обработчик в этом же сервисе, см. §12).
- **Переход**: → `PENDING_PAYMENT` (или возврат в `DRAFT` после `ReservationFailed`).
- **Ошибки**: `ORDER_INVALID_STATE`, `ORDER_BELOW_MINIMUM`, `EMPTY_ORDER`.

### `MarkShipped`

- **Класс**: `MarkShippedUseCase implements UseCaseCommand<EmptyResult>`.
- **Инициатор**: продавец (через Seller BFF).
- **Параметры**: `orderId`, `shipmentRef` (трек-номер у логиста).
- **Проверки**: статус = `PAID`, `BR-005` (продавец имеет позицию в заказе).
- **Действия**: `order.ship(shipmentRef)` → `OrderShipped` → save + Outbox.
- **Переход**: `PAID → SHIPPED`.

### `MarkDelivered`

- **Класс**: `MarkDeliveredUseCase implements UseCaseCommand<EmptyResult>`.
- **Инициатор**: служба доставки (system role) или admin.
- **Действия**: `order.deliver()` → `OrderDelivered` → save + Outbox.
- **Переход**: `SHIPPED → DELIVERED`. Запускает таймер 14 дней через `delivered_orders_timeline`.

### `CancelOrder`

- **Класс**: `CancelOrderUseCase implements UseCaseCommand<OrderJsonBean>`.
- **Инициатор**: владелец заказа или admin.
- **Проверки**: статус ∈ `{DRAFT, PENDING_PAYMENT, PAID, SHIPPED}`. `BR-006` (после `SHIPPED` — через возврат).
- **Действия**:
  - В `DRAFT` / `PENDING_PAYMENT`: снять резерв (если был) → `OrderCancelled`.
  - В `PAID` / `SHIPPED`: запустить Saga `ProcessRefund` (см. §12).
- **Переход**: → `CANCELLED` или `CANCELLED → REFUNDED` (после саги).

### `OpenDispute`

- **Класс**: `OpenDisputeUseCase implements UseCaseCommand<EmptyResult>`.
- **Параметры**: `orderId`, `reason`, `attachments` (фото).
- **Проверки**: статус = `DELIVERED`, в окне 14 дней (`BR-007`).
- **Действия**: `order.openDispute(reason)` → `DisputeOpened` → save + Outbox; уведомления продавцу (3 дня на ответ).
- **Переход**: `DELIVERED → DISPUTE`.

### `ResolveDisputeForBuyer` / `ResolveDisputeForSeller`

- **Класс**: `ResolveDisputeUseCase` (с параметром `decision`).
- **Инициатор**: только `admin`.
- **Действия**: `order.resolveDispute(decision)` → `DisputeResolved` (с указанием стороны) → save + Outbox.
- **Переход**: `DISPUTE → REFUNDED` или `DISPUTE → COMPLETED`. На `REFUNDED` — запускается Saga `ProcessRefund`.

### Внутренние обработчики (event-driven)

Эти не являются «командами» в смысле API — это event handlers, реализованные как `UseCaseCommand` для единообразия:

- `HandlePaymentSucceeded` — слушает `PaymentSucceeded` из Kafka, переводит `PENDING_PAYMENT → PAID`. Idempotent (`BR-011`).
- `HandlePaymentFailed` — слушает `PaymentFailed`, возвращает в `DRAFT`, снимает резерв.
- `HandleItemReserved` — слушает `ItemReserved`, фиксирует `reservationId` в `Order`. Только перевод статуса в `PENDING_PAYMENT` (после уже состоявшегося `ConfirmOrder`).
- `HandleReservationFailed` — слушает `ReservationFailed`, возвращает в `DRAFT`, эмиссия `OrderReservationFailed`.
- `ExpireUnpaidOrdersJob` — `@Scheduled`, периодически переводит протухшие `PENDING_PAYMENT → EXPIRED`, снимает резерв.
- `CloseDeliveredOrdersJob` — `@Scheduled` ежедневно, переводит `DELIVERED → COMPLETED` через 14 дней.
