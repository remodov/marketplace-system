---
type: context-section
context: order-service
parent: "[[order-service]]"
section: ui
tier: C
tags:
  - ui
  - bc/order
---

## 11. UI-спецификация

> Order Service сам по себе UI не имеет — экраны живут в **Customer BFF**, **Seller BFF** и **Admin BFF**. Этот раздел фиксирует, какие экраны и тексты завязаны на статусы и ошибки Order Service. Дизайн-макеты — в Figma за пределами репозитория.

### Экраны, завязанные на Order

| Экран | Канал | Команды / запросы Order |
|---|---|---|
| Корзина | Customer BFF | (нет — корзина живёт в BFF, не в Order) |
| Подтверждение заказа | Customer BFF | `CreateOrderUseCase` → отображает `total`, адрес, позиции |
| Оплата | Customer BFF | `ConfirmOrderUseCase` → редирект на форму платёжного шлюза |
| Заказ оформлен | Customer BFF | `GetOrderByIdQuery` → показ статуса (`PAID`/`PENDING_PAYMENT`) |
| Список моих заказов | Customer BFF | `SearchMyOrdersQuery` |
| Деталь заказа | Customer BFF | `GetOrderByIdQuery` + `GetOrderTimelineQuery` |
| Открыть спор | Customer BFF | `OpenDisputeUseCase` |
| Список заказов продавца | Seller BFF | `SearchSellerOrdersQuery` |
| Кнопка «Отправлено» | Seller BFF | `MarkShippedUseCase` |
| Очередь споров | Admin BFF | `SearchAllOrdersQuery` (фильтр `status=DISPUTE`) |
| Карточка спора | Admin BFF | `GetOrderByIdQuery` + `GetOrderTimelineQuery` + `ResolveDisputeUseCase` |

### Связь UI ↔ статусы

| Статус | Бейдж в UI | Цвет |
|---|---|---|
| `DRAFT` | «Черновик» | серый |
| `PENDING_PAYMENT` | «Ожидает оплаты» | жёлтый |
| `PAID` | «Оплачен» | синий |
| `SHIPPED` | «Отправлен» | фиолетовый |
| `DELIVERED` | «Доставлен» | зелёный |
| `COMPLETED` | «Завершён» | зелёный (тёмный) |
| `EXPIRED` | «Истёк» | серый (тёмный) |
| `CANCELLED` | «Отменён» | серый (тёмный) |
| `DISPUTE` | «Спор» | красный |
| `REFUNDED` | «Возврат» | серый-синий |

### Тексты ошибок (показывает BFF, см. также §13)

| Код | Текст пользователю |
|---|---|
| `OUT_OF_STOCK` | «Один из товаров закончился: <название>. Уберите его и попробуйте снова.» |
| `PRODUCT_NOT_FOUND` | «Этот товар больше не продаётся.» |
| `ORDER_INVALID_STATE` | «Действие недоступно для этого заказа.» |
| `PAYMENT_FAILED` | «Оплата не прошла — попробуйте другую карту или способ.» |
| `PAYMENT_TIMEOUT` | «Не удалось подтвердить оплату — мы вернёмся к вам в течение 5 минут.» |
| `PROMO_INVALID` | «Промокод недействителен.» |
| `PROMO_NOT_APPLICABLE` | «Промокод не применим к этой корзине.» |
| `PROMO_ALREADY_APPLIED` | «К этому заказу уже применён промокод.» |
| `REFUND_TOO_LATE` | «Срок возврата истёк (14 дней с момента получения).» |
| `ORDER_BELOW_MINIMUM` | «Минимальная сумма заказа — 100 ₽.» |
| `MULTI_SELLER_NOT_SUPPORTED` | «В одном заказе товары только одного продавца. Оформите второй заказ.» |
| `FORBIDDEN` | «Этот заказ недоступен.» |
| `UNAUTHORIZED` | «Войдите в аккаунт.» |

### Дизайн-система

Используется общий компонент `<OrderStatusBadge status={…} />` с маппингом «статус → цвет/текст» по таблице выше. Текст бейджа никогда не задаётся вручную в JSX — только через статус из API.
