---
type: context-section
context: order-service
parent: "[[order-service]]"
section: nfr
tier: C
tags:
  - nfr
  - sla
  - security
  - observability
  - bc/order
---

## 16. Нефункциональные требования

### Производительность

| Операция | Цель | Метрика | Алёрт |
|---|---|---|---|
| `CreateOrderUseCase` | p95 < 1.5s | `usecase_duration_seconds{usecase="CreateOrder"}` | p95 > 2s 5 минут |
| `ConfirmOrderUseCase` | p95 < 800ms | `usecase_duration_seconds{usecase="ConfirmOrder"}` | p95 > 1.5s |
| `GetOrderByIdQuery` | p95 < 100ms (Read Model) | `usecase_duration_seconds{usecase="GetOrderById"}` | p95 > 300ms |
| `SearchMyOrdersQuery` | p95 < 200ms | то же | p95 > 500ms |
| Outbox-relay лаг | < 1s | `outbox_lag_seconds` | > 5s 5 минут |
| Read Model лаг | < 1s от Outbox | `read_model_lag_seconds` | > 10s |

**Throughput**: до 500 RPS на оформление заказов в пик (Чёрная пятница). Горизонтальное масштабирование Order Service до 10 реплик за gateway.

### Доступность

- **SLO Order Service:** 99.9% availability (≈ 8.76 часов простоя в год).
- **Зависимости:**
  - PostgreSQL primary + 2 hot standby (streaming replication, RPO ≤ 5s).
  - Kafka cluster 3+ брокера.
  - Catalog, Payment, Inventory — circuit breaker и graceful degradation.

### Согласованность

- Внутри агрегата — strong consistency через PostgreSQL транзакции.
- Между агрегатами — eventual consistency через Kafka. Лаг до 5 секунд — допустим, более — алёрт.
- Read Model — eventual consistency от Outbox. Read-your-own-writes для critical UI операций — через прямой запрос к write-side.

### Безопасность

- **Транспорт:** TLS 1.3 для внешнего трафика, mTLS между внутренними сервисами.
- **Аутентификация:** JWT от Keycloak, валидация JWK Set с кэшем (5 мин).
- **Авторизация:** RBAC на gateway (роли `customer`/`seller`/`admin`/`system`); ABAC внутри Order по владению.
- **Audit log:** все команды от `admin` пишутся в таблицу `order_audit_log` с `who`/`when`/`what`. Retention — 5 лет.
- **PII:**
  - `Address` (адрес доставки) — шифрование at-rest (PostgreSQL TDE + столбец `pgcrypto.AES256`).
  - `customerId` — UUID (не PII сам по себе, не позволяет идентифицировать без User-сервиса).
  - `email`/`phone` — **НЕ хранятся в Order**, тянутся из User-сервиса при необходимости.
- **Удаление:** по требованию покупателя (152-ФЗ) — анонимизация старых заказов через 5 лет после `COMPLETED`/`REFUNDED`. `customer_id` не удаляется (целостность ссылок), но связь с реальным человеком — только через User-сервис, где данные удалены.
- **Compliance:** 152-ФЗ обязательно. PCI DSS не применяется (платёжные данные — в Payment Service, где шифрование и токенизация).

### Наблюдаемость

| Сигнал | Источник | Дашборд |
|---|---|---|
| `usecase_*` метрики | Micrometer (`usecase-pattern-starter`) | "Order – UseCase performance" |
| `outbox_lag_seconds` | custom metric | "Order – Outbox" |
| `processed_events_total{kind=duplicate}` | Idempotent Consumer | "Order – Idempotency" |
| `orders_status_count` | scheduled scrape | "Order – State distribution" |
| `kafka_consumer_lag_*` | Kafka exporter | "Order – Kafka" |
| Distributed tracing | OpenTelemetry → Tempo | "Trace по `correlation-id` (= orderId)" |
| Логи | Loki, JSON структурный | поле `orderId` в каждом логе |

**Алёрты (P1)**: outbox lag > 5 минут; circuit breaker открыт > 5 минут; `orders_paid_lag_5m > 30s`; ошибка > 5% запросов 5 минут.

### Капасити

| Сущность | На 1 месяц | На 6 месяцев | На 12 месяцев |
|---|---|---|---|
| Заказов в `orders` | 15 млн | 90 млн | 180 млн |
| Записей `order_items` (×3 в среднем) | 45 млн | 270 млн | 540 млн |
| Outbox строк | 50 млн | 300 млн | 600 млн (с partition rotation) |
| Read Model | следует за write |
| Kafka offset retention | 7 дней | 7 дней | 7 дней |

PostgreSQL партиционирование `orders` по `created_at` (по месяцам) после 6 млн записей.

### Compliance

- **152-ФЗ (РФ)** — согласие, право на удаление, аудит.
- **PCI DSS** — не применяется (Order не видит реквизиты карт; всё на стороне Payment Service).
- **Sanctioned regions** — на этапе Catalog/User-сервисе; Order не делает дополнительных проверок.
