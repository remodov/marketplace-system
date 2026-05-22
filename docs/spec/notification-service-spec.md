---
context: notification-service
bounded-context: Notification
---

# Notification Service — Use Case спецификация (Tier A)

Tier A: слоёная архитектура (Controller → Service → Repository) без UseCase Pattern и DDD. **Агрегатов нет** (anemic, CRUD-сервис) — вся спека в одном файле, контекст- и модуль-секции идут единой сквозной нумерацией; §Доменные события (публикация) и §Процессы помечены «не применимо». Задача сервиса — взять входящее событие Order, отрендерить шаблон, отправить во внешний канал, записать результат.

---

## 1. Bounded Context

**Контекст:** Notification (на Tier A — **модуль**, не отдельная предметная область). **Субдомен:** Generic. **Владелец:** команда «Платформа». Агрегатов нет.

**Внутри границы:** подписка на доменные события Order; выбор каналов по типу события; рендер шаблона и отправка через SMTP + FCM; журнал попыток; ретраи при временных ошибках; приём webhook'ов о доставке; админ-журнал и ручной retry.

**Вне границы:** бизнес-логика маркетплейса (правила «когда слать» — в источнике события); настройки подписок/опт-ауты (Tier B); кампании/массовые рассылки (отдельный сервис — здесь только транзакционные); токены устройств (берёт у Customer BFF); тело письма после отправки (только метаданные).

---

## 2. Интеграции (Context Map)

```mermaid
flowchart LR
    Order["Order Service"]
    Notif(("Notification"))
    BFF["Customer BFF"]
    SMTP["SMTP (Mailgun)"]
    FCM["FCM (Firebase)"]
    Admin["Оператор"]
    Order -->|conformist| Notif
    Notif -->|conformist| BFF
    Notif -->|outbound| SMTP
    SMTP -->|webhook| Notif
    Notif -->|outbound| FCM
    Admin -->|inbound| Notif
```

| Ребро | Направление | Канал | Связь | Передаётся |
|---|---|---|---|---|
| Order Service | inbound | async | conformist | события `marketplace.orders.v1` |
| Customer BFF | outbound | sync | conformist | `GET /users/{id}/contact` — email, push-токены, locale |
| SMTP (Mailgun) | bidirectional | sync + webhook | внешний | отправка письма; webhook `delivered`/`bounced` |
| FCM (Firebase) | outbound | sync | внешний | push (fire-and-forget) |
| Admin UI | inbound | sync | customer-supplier | журнал, ручной retry |

Notification — **Conformist** к контрактам Order и Customer BFF: подстраивается, не диктует. ACL на Tier A избыточен.

### Контракты

| Контракт | Формат | Файл | Владелец |
|---|---|---|---|
| События Order (consume) | AsyncAPI | контракт контекста Order (`marketplace.orders.v1`) | Order |
| Customer BFF API (consume) | OpenAPI | контракт контекста Customer BFF | Customer BFF |
| Notification REST (admin + webhook) | OpenAPI | `contracts/notification-api.openapi.yaml` | Notification |

---

## 3. Ubiquitous Language

| Термин | В коде | Определение |
|---|---|---|
| Уведомление | `Notification` | Запись журнала: одно событие, одна попытка в один канал одному адресату. |
| Канал | `Channel` | `EMAIL`, `PUSH` (на запуске); `SMS` — расширение. |
| Адресат | `Recipient` | `userId` + материализованный контакт на момент отправки. |
| Шаблон | `Template` | Сообщение в БД: ключ + язык + субъект и тело с `${var}`. |
| Исходное событие | `SourceEvent` | Входящее Kafka-событие (JSON хранится для дебага/retry). |
| Статус доставки | `DeliveryStatus` | `QUEUED`/`SENT`/`DELIVERED`/`BOUNCED`/`FAILED` (§5). |

Намеренно нет агрегатов, value objects, доменных событий — `Notification` это строка в таблице.

---

## 4. Доменная модель

Tier A — модель = **таблицы** (агрегатов/VO нет; проектное решение). Контроллер/сервис принимают JSON-DTO напрямую.

| Таблица | Роль |
|---|---|
| `notifications` | главная: строка = попытка доставки в один канал; копия контакта и шаблонных переменных на момент отправки |
| `templates` | шаблоны (`<event>.<channel>` × locale): субъект + тело с `${var}` |
| `delivery_attempts` | лог каждой попытки (изолирован от `notifications` — запросы по статусу по индексу) |
| `processed_events` | идемпотентность консьюмера (PK по `event_id`) |

Схема (ER, типы, индексы) — §Техническая реализация.

---

## 5. Жизненный цикл уведомления

| Статус | Описание |
|---|---|
| `QUEUED` | создано из события, ждёт отправки |
| `SENT` | ушло в провайдер |
| `DELIVERED` | webhook `delivered` (только email) |
| `BOUNCED` | webhook `bounced` |
| `FAILED` | ретраи исчерпаны / permanent error |

```mermaid
stateDiagram-v2
    [*] --> QUEUED: создано из события
    QUEUED --> SENT: ушло в провайдер
    QUEUED --> FAILED: 3 ретрая исчерпаны
    SENT --> DELIVERED: webhook delivered (email)
    SENT --> BOUNCED: webhook bounced
    FAILED --> QUEUED: ручной retry оператора
    BOUNCED --> [*]
    DELIVERED --> [*]
    FAILED --> [*]: оператор пометил «безнадёжно»
```

Терминальные: `DELIVERED`, `BOUNCED`, `FAILED` (после ручной отметки). PUSH без webhook → остаётся `SENT` (`BR-N7`). Ручной retry — только из `FAILED`.

---

## 6. Роли и доступ

| Роль | Кто |
|---|---|
| `support-operator` | оператор поддержки (Keycloak) |
| `system` | SMTP-провайдер (webhook, HMAC), Customer BFF (s2s) |

| Операция | support-operator | system |
|---|---|---|
| `GET /notifications`, `/{id}` | ✅ | — |
| `POST /{id}/retry`, `/{id}/abandon` | ✅ | — |
| `POST /webhooks/email-events` | — | ✅ (HMAC) |

ABAC отсутствует — оператор видит все уведомления. Покупатели прямого доступа не имеют (inbox — через Customer BFF, s2s).

**PII:** email/push-токен — шифрование хранения; в логах маскируются; TTL 90 дней (`BR-N8`).

---

## 7. Бизнес-правила

- **`BR-N1`** — *инвариант.* Идемпотентность по `event_id` (`processed_events`, `INSERT … ON CONFLICT DO NOTHING`).
- **`BR-N2`** — *политика.* Каналы по типу события (таблица в коде): `OrderConfirmed`/`OrderPaid`/`OrderShipped`/`OrderCancelled`/`OrderRefunded`/`DisputeResolved` → email+push; `OrderDelivered` → email; `DisputeOpened` → push продавцу + email оператору.
- **`BR-N3`** — *инвариант.* Нет шаблона для `(event, channel, locale)` → уведомление не создаётся + `notification_template_missing_total`.
- **`BR-N4`** — *инвариант.* Контакт материализуется на момент отправки.
- **`BR-N5`** — *политика.* Retry 3 попытки (30s/5min/30min) при `TRANSIENT_ERROR`; `PERMANENT_ERROR` → сразу `FAILED`.
- **`BR-N6`** — *инвариант.* Дедупликация webhook по `(notification_id, webhook_event_id)`.
- **`BR-N7`** — *инвариант.* PUSH без подтверждения: 200 от FCM → `SENT`, не переходит в `DELIVERED`.
- **`BR-N8`** — *политика.* TTL журнала 90 дней (152-ФЗ/GDPR).
- **`BR-N9`** — *политика.* ≤ 100 писем/сек на провайдера (token bucket).

---

## 8. Команды (операции)

Tier A — операции сервиса/шедулеров, не `UseCase`-классы.

### `ProcessOrderEvent`
- **Триггер:** Kafka `marketplace.orders.v1` · **Предусловия:** `BR-N1`, `BR-N3`
- **Логика:** проверить `event_id`; запросить контакт у Customer BFF; по таблице каналов (`BR-N2`) создать записи `QUEUED`.
- **Ошибки:** `CONTACT_LOOKUP_FAILED` (→ `FAILED` после ретраев), `TEMPLATE_MISSING` (не создаётся)

### `DispatchPending`
- **Триггер:** шедулер (1с) · **Логика:** взять `QUEUED` (≤100), отрендерить, отправить, обновить статус; при ошибке — `delivery_attempts` + retry (`BR-N5`).

### `ProcessEmailWebhook`
- **Триггер:** webhook Mailgun · **Предусловия:** HMAC (`BR-N6`) · **Логика:** по `external_id` → `DELIVERED`/`BOUNCED`.
- **Ошибки:** `WEBHOOK_SIGNATURE_INVALID`

### `RetryNotification`
- **Актор:** support-operator · **Переход:** `FAILED` → `QUEUED` · **Ошибки:** `NOTIFICATION_NOT_FOUND`, `INVALID_STATUS_FOR_RETRY`

### `AbandonNotification`
- **Актор:** support-operator · **Переход:** `FAILED` → `FAILED` (финальный, без retry)

### `PurgeOldRecords`
- **Триггер:** шедулер (ежедневно) · **Логика:** удалить записи старше 90 дней (`BR-N8`).

---

## 9. Доменные события

**Не применимо на Tier A.** Сервис событий не публикует — только потребляет события Order (§Интеграции).

---

## 10. Запросы

### `SearchNotifications`
- **Актор:** support-operator · **Параметры:** `userId?`, `status?`, `eventType?`, `channel?`, период, пагинация
- **Возвращает:** страницу уведомлений · **Логика:** SELECT поверх `notifications` (Read Model на Tier A нет).

### `GetNotification`
- **Актор:** support-operator · **Параметры:** `id` · **Возвращает:** запись + `delivery_attempts`.

### `GetUserInbox`
- **Актор:** Customer BFF (s2s) · **Параметры:** `userId`, пагинация · **Возвращает:** уведомления пользователя по дате.

---

## 11. Use Cases

**UC-N1 Подтверждение заказа → email + push.** `OrderConfirmed` → проверка `event_id` → контакт у BFF → две записи `QUEUED` (`BR-N2`) → отправка → email `SENT` → webhook → `DELIVERED`; push `SENT`.

**UC-N2 Провайдер 503 → retry.** 3 попытки (`BR-N5`) → `FAILED` + алёрт → оператор `RetryNotification` → успех.

**UC-N3 Невалидный адрес → bounced.** email `SENT` → webhook `bounced` → `BOUNCED`.

**UC-N4 Разбор оператора.** Жалоба → фильтр по `userId` → запись `BOUNCED` → детали → ответ.

---

## 12. Процессы

**Не применимо на Tier A.** Межагрегатных/межсервисных процессов нет — каждое событие обрабатывается независимо.

---

## 13. UI-спецификация

Один интерфейс — админ-журнал для `support-operator` (пользователи inbox видят через Customer BFF).

- **Журнал** (`/admin/notifications`): фильтры, таблица (дата/получатель/канал/тип/статус/действия), пагинация, счётчики.
- **Детали** (`/admin/notifications/{id}`): поля + `source_event_payload` + `delivery_attempts`; Retry/Abandon (если `FAILED`).

| Код ошибки | Текст оператору |
|---|---|
| `INVALID_STATUS_FOR_RETRY` | Можно ретраить только FAILED-уведомления. |
| `NOTIFICATION_NOT_FOUND` | Уведомление не найдено. |

---

## 14. Критерии приёмки (Given / When / Then)

- **Given** событие Order из таблицы каналов · **When** консьюмер получил · **Then** записи в правильных каналах (`BR-N2`).
- **Given** обработанное событие · **When** Kafka доставляет дубль · **Then** второе не создаётся (`BR-N1`).
- **Given** отправленное письмо · **When** webhook `delivered`/`bounced` · **Then** `SENT` → `DELIVERED`/`BOUNCED`.
- **Given** webhook без валидной HMAC · **When** приём · **Then** `WEBHOOK_SIGNATURE_INVALID` (401).
- **Given** провайдер 5xx · **When** отправка · **Then** 3 ретрая → `FAILED`; 4xx → сразу `FAILED`.
- **Given** нет шаблона · **When** обработка · **Then** не создаётся + `notification_template_missing_total++`.
- **Given** уведомление `FAILED` · **When** `POST /retry` · **Then** `QUEUED`; на не-`FAILED` → 409.
- **Given** Customer BFF недоступен · **When** запрос контакта · **Then** `FAILED`; retry после восстановления BFF.

---

## 15. Нефункциональные требования

| Аспект | Требование |
|---|---|
| Производительность | до 500 событий/с → ~1000 уведомлений; p95 «событие → ушло в Mailgun» ≤ 5с; p99 ≤ 30с |
| Надёжность | at-least-once; идемпотентность консьюмера (`BR-N1`); потерянные `QUEUED` подберёт следующий запуск |
| Безопасность | HMAC всех webhook (timestamp от replay); PII шифруется + маскируется; TTL 90 дней; s2s к BFF через service-account JWT |
| Наблюдаемость | метрики created/status/provider_latency/template_missing/retry; алёрты `failed > 5/min`, `template_missing > 0`, `latency p99 > 10с`; трейс по `event_id`/`notification_id` |
| Эксплуатация | шаблоны без рестарта (кэш TTL ≤ 60с); новый провайдер = adapter; locale `ru`/`en` (32 шаблона) |

---

## 16. Техническая реализация

`java-21` · `spring-boot-3` · `spring-kafka` (консьюмер) · `spring-web` (webhook + admin) · `spring-security` (OAuth2 + HMAC-фильтр) · `postgresql-16` + `jooq` + `flyway` · Mailgun/FCM (REST) · `resilience4j` (CB/retry для BFF) · Micrometer/Prometheus · OpenTelemetry · JUnit5 + WireMock.

**Persistence — только jOOQ, только сгенерированные классы** (`BS-17`, на любом Tier). Postgres ENUM-типы (`notification_channel`/`notification_status`/`delivery_attempt_result`) → jOOQ генерит Java-enum. Tier A — про отсутствие DDD/UseCase Pattern, не про упрощение persistence.

### Схема БД

```mermaid
erDiagram
    notifications ||--o{ delivery_attempts : has
    notifications {
        uuid id PK
        uuid event_id
        text event_type
        uuid user_id
        notification_channel channel
        text contact
        text template_key
        text locale
        notification_status status
        jsonb source_event_payload
        jsonb template_variables
        text external_id
        timestamptz created_at
        timestamptz sent_at
        timestamptz delivered_at
        text last_error
    }
    templates {
        text key PK
        text locale PK
        text subject
        text body
        timestamptz updated_at
    }
    delivery_attempts {
        uuid id PK
        uuid notification_id FK
        integer attempt_number
        delivery_attempt_result result
        text response_snippet
        timestamptz attempted_at
    }
    processed_events {
        uuid event_id PK
        timestamptz processed_at
    }
```

**API-контракт ошибок** — RFC 9457 ProblemDetails: `NOTIFICATION_NOT_FOUND` (404), `INVALID_STATUS_FOR_RETRY` (409), `WEBHOOK_SIGNATURE_INVALID` (401). Внутренние (`TEMPLATE_MISSING`, `CONTACT_LOOKUP_FAILED`, `PROVIDER_UNAVAILABLE`) наружу не светят — пишутся в `last_error`.

**Расширения (Tier B/C):** подписки/опт-ауты, A/B-тексты, WebPush, кампании (отдельный сервис), динамический выбор каналов через админку.
