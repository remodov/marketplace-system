---
type: context-section
context: order-service
parent: "[[order-service]]"
section: use-cases
tier: C
tags:
  - use-cases
  - bc/order
---

## 10. Use Cases (сценарии использования)

### UC-1: Покупка — счастливый сценарий

**Актор**: Покупатель (через Customer BFF).

**Триггер**: покупатель нажимает «Оформить заказ» из корзины.

**Основной поток**:

1. Customer BFF вызывает `POST /api/v1/orders` с заголовком `Idempotency-Key`.
2. `CreateOrderUseCase` создаёт `Order` в статусе `DRAFT`, валидирует цены через Catalog (`BR-001`, `BR-014`).
3. Order возвращает `{ orderId, total }` в BFF; покупатель видит экран подтверждения.
4. Покупатель нажимает «Подтвердить и оплатить» → BFF вызывает `POST /api/v1/orders/{id}/confirm`.
5. `ConfirmOrderUseCase` проверяет инварианты (`BR-002`, `BR-013`), переводит в `PENDING_PAYMENT`, публикует `OrderConfirmed` через Outbox.
6. Inventory подписан на `OrderConfirmed` → резервирует остаток → публикует `ItemReserved`. Order ловит событие, сохраняет `reservationId` (см. §12 Saga).
7. BFF параллельно (после шага 5) вызывает `POST /payments` в Payment Service → переход на платёжную форму.
8. Покупатель оплачивает; Payment публикует `PaymentSucceeded`.
9. Order ловит `PaymentSucceeded` (handler `HandlePaymentSucceeded`), переходит `PENDING_PAYMENT → PAID`, публикует `OrderPaid`.
10. Notification (подписан) шлёт чек покупателю и уведомление продавцу.
11. Продавец видит заказ в кабинете, собирает посылку, нажимает «Отправлено» → `MarkShipped` → `OrderShipped`.
12. Курьер вручает → `MarkDelivered` (system role) → `OrderDelivered`. Запускается таймер 14 дней.
13. Через 14 дней без спора — `CloseDeliveredOrdersJob` переводит в `COMPLETED`, Settlement начисляет выручку продавцу.

**Альтернативный поток — резерв не удался (`BR-002`)**:

- На шаге 6 Inventory публикует `ReservationFailed` (один из товаров кончился).
- Order возвращает заказ в `DRAFT`, публикует `OrderReservationFailed`.
- BFF показывает покупателю «Один из товаров закончился: <название>. Уберите его и попробуйте снова».

**Альтернативный поток — оплата не прошла**:

- На шаге 9 приходит `PaymentFailed`. Order возвращает в `DRAFT`, снимает резерв.
- Покупатель может попробовать другую карту: `POST /api/v1/orders/{id}/confirm` повторно (новая попытка резерва).

**Альтернативный поток — покупатель ушёл с формы оплаты**:

- На шаге 9 события не приходит. Через 15 минут срабатывает `ExpireUnpaidOrdersJob`, переводит в `EXPIRED`, снимает резерв.

### UC-2: Отмена до отправки

**Актор**: Покупатель.

**Предусловие**: заказ в `PAID` (не `SHIPPED`).

**Поток**:

1. `POST /api/v1/orders/{id}/cancel`.
2. `CancelOrderUseCase` проверяет ABAC и статус.
3. Запускает Saga `ProcessRefund` (см. §12).
4. Saga отменяет резерв в Inventory и инициирует возврат денег в Payment.
5. После `RefundIssued` от Payment — заказ `→ REFUNDED`.
6. Notification сообщает покупателю об успехе возврата.

### UC-3: Возврат после получения (открытие спора)

**Актор**: Покупатель.

**Предусловие**: заказ в `DELIVERED`, прошло ≤ 14 дней.

**Поток**:

1. `POST /api/v1/orders/{id}/disputes` с фото и описанием.
2. `OpenDisputeUseCase` проверяет окно (`BR-007`), переводит `DELIVERED → DISPUTE`.
3. `DisputeOpened` публикуется → Notification уведомляет продавца, у него 3 дня на ответ.
4. **Альтернатива А — продавец согласился**: оператор закрывает спор в пользу покупателя → `ResolveDisputeForBuyer` → Saga `ProcessRefund` → `REFUNDED`.
5. **Альтернатива Б — продавец оспорил**: оператор смотрит детали, выносит решение. В пользу покупателя → как А; в пользу продавца → `ResolveDisputeForSeller` → `COMPLETED`, выручка продавцу.

### UC-4: Продавец отмечает отправку

**Актор**: Продавец.

**Предусловие**: заказ в `PAID`, продавец имеет ≥ 1 позицию в заказе.

**Поток**:

1. Продавец логинится в Seller BFF, видит список своих заказов в `PAID`.
2. Выбирает заказ, вводит `shipmentRef` (трек-номер у логиста), нажимает «Отправлено».
3. `POST /api/v1/orders/{id}/ship { shipmentRef }` через Seller BFF.
4. `MarkShippedUseCase` переводит в `SHIPPED`, публикует `OrderShipped`.
5. Notification уведомляет покупателя с трек-номером.

### UC-5: Покупатель смотрит свои заказы

**Актор**: Покупатель.

**Поток**:

1. `GET /api/v1/orders?role=customer&status=PAID,SHIPPED,DELIVERED&page=0&size=20`.
2. `SearchMyOrdersQuery` через Read Model (`order_summaries`), фильтр по `customer_id == jwt.sub`.
3. Возвращает страницу `OrderSummaryJson` для UI-списка.

### UC-6: Продавец смотрит свои заказы

**Актор**: Продавец.

**Поток**:

1. `GET /api/v1/orders?role=seller&status=PAID&page=0`.
2. `SearchSellerOrdersQuery` фильтрует `primary_seller_id == jwt.sub`.
3. Кэш на 30 секунд (`@Cacheable("seller-orders")`).

### UC-7: Оператор закрывает спор

**Актор**: admin.

**Поток**:

1. Admin BFF показывает очередь споров (`status = DISPUTE`).
2. Оператор открывает заказ, читает таймлайн (`GetOrderTimelineQuery`), смотрит фото и переписку.
3. Принимает решение: `POST /api/v1/orders/{id}/disputes/resolve { decision: BUYER | SELLER }`.
4. `ResolveDisputeUseCase` обрабатывает решение, переходит в `REFUNDED` или `COMPLETED`.
