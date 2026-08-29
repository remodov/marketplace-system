# Notification Service — эталонная реализация Tier A

Этот репозиторий — один из сервисов сквозного кейса маркетплейса с
[vikulin-va.ru](https://vikulin-va.ru/case/), реализованный по методологии
[Use Case Pattern](https://vikulin-va.ru/use-case-pattern/) на **Tier A** —
без библиотеки `usecase-pattern` и без DDD. Контр-пример к
[order-service](https://github.com/remodov/order-service) (Tier C):
показывает, что не каждому сервису нужны агрегаты и саги.

## Что внутри

1. **Use Case спецификация** — `docs/spec/`, в формате 13 файлов (из 17
   разделов шаблона два пропущены: `08-events` и `12-sagas`). Это
   контракт между бизнесом и разработкой; машинно-читаемые теги во
   фронт-маттере позволяют скриптам строить C4 и проверять полноту.
   Полный текст одной страницей —
   [vikulin-va.ru/case/notification-service](https://vikulin-va.ru/case/notification-service/).
2. **Код** — Spring Boot модуль (будет наполнен скиллами по спеке).
   Стек: Spring Web + Kafka, JdbcTemplate (без JPA — это Tier A),
   PostgreSQL, Liquibase, Mailgun + Firebase для outbound, Resilience4j
   для circuit-breaker.

## Структура спеки

```
docs/spec/
    notification-service.md                    # индекс (type: service, tier: A)
    01-notification-service-context.md          # модуль / соседи (без BC, на Tier A)
    02-notification-service-language.md         # короткий глоссарий
    03-notification-service-model.md            # ER + таблицы (без агрегатов)
    04-notification-service-lifecycle.md        # state machine: QUEUED → SENT → DELIVERED|BOUNCED|FAILED
    05-notification-service-roles.md            # support-operator + Mailgun webhook
    06-notification-service-rules.md            # BR-N1..BR-N9
    07-notification-service-commands.md         # список операций (НЕ UseCase-записи)
    # 08-events — пропущен (Tier A не публикует доменных событий)
    09-notification-service-queries.md          # список read-операций
    10-notification-service-use-cases.md        # 4 сквозных сценария
    11-notification-service-ui.md               # админка для оператора
    # 12-sagas — пропущен (Tier A не координирует процессов)
    13-notification-service-errors.md           # каталог ошибок (RFC 9457)
    14-notification-service-integrations.md     # Kafka + REST + Mailgun + FCM
    15-notification-service-acceptance.md       # AC-N1..AC-N12
    16-notification-service-nfr.md              # производительность, безопасность, observability
    17-notification-service-stack.md            # технологический стек + обоснование JdbcTemplate
```

## Зачем Tier A как пример

Order Service показывает «полную методологию»: домен, агрегаты, события,
саги, hexagonal-разделение. Но **большинство сервисов в любой реальной
системе устроены проще**: они переводят данные между шинами и API.
Notification — типичный такой: подписался на Kafka, отрендерил шаблон,
отправил во внешний канал, записал результат в Postgres. Применять к нему
DDD — оверкилл, а потом ходить и поддерживать «pseudo-агрегаты» — больно.

Tier A декларирует, что отсутствие агрегатов и саг — не пробел в спеке,
а **проектное решение**. Спека от этого не страдает: 13 разделов вместо
17, и читателю сразу видно, что это за класс задачи.

## Quickstart

```bash
# 1. Postgres из docker-compose
docker compose up -d postgres

# 2. Sanity-build без интеграционных тестов
./gradlew build -x test

# 3. Все тесты (нужен Postgres)
./gradlew test

# 4. Локальный запуск
./gradlew bootRun --args='--spring.profiles.active=local'
```

Сервис слушает `:8090`. Liquibase сам прогонит миграции из
`migrations/db/changelog-master.yaml` и засеет 14 базовых шаблонов.

### Профили

| Профиль | Когда применять | Что включает |
|---|---|---|
| `local` | `./gradlew bootRun` для ручной разработки | Postgres из docker-compose (порт 5433), security permitAll, Kafka listeners off, Mailgun/FCM dev-плейсхолдеры |
| `integration-test` | Только в `@SpringBootTest` | Postgres из docker-compose, WireMock-стабы для Customer BFF / Mailgun / FCM на фиксированных портах, schedulers заглушены |
| (без профиля) | Production | Реальный IdP, Kafka, Customer BFF, Mailgun, FCM (все URL-ы и ключи через ENV) |

### Что реализовано

| Что | Где |
|---|---|
| 4 таблицы + 14 seed-шаблонов | `migrations/db/changelog/v-1.0/initial-schema.yaml` |
| 8 событий → email + push | `channel.ChannelRules` (BR-N2) |
| Идемпотентный консьюмер | `consumer.OrderEventConsumer` + `processed_events` (BR-N1) |
| Шедулеры dispatch + purge | `scheduler.DispatchPendingScheduler`, `PurgeOldRecordsScheduler` |
| Mailgun + FCM провайдеры | `provider.email.MailgunEmailProvider`, `provider.push.FcmPushProvider` |
| Webhook delivered/bounced | `webhook.MailgunWebhookController` (HMAC валидация) |
| Admin REST | `controller.AdminNotificationController` (роль `support-operator`) |
| Тесты | 18 интеграционных, все зелёные |

## Связанные артефакты

- [Order Service](https://github.com/remodov/order-service) — Tier C, источник событий, которые потребляет Notification.
- [Use Case Pattern: методология](https://vikulin-va.ru/use-case-pattern/)
- [Универсальный шаблон спеки](https://vikulin-va.ru/use-case-pattern/spec-template/) — показывает, какие разделы где обязательны.
- Бизнес-кейс — [vikulin-va.ru/case/](https://vikulin-va.ru/case/)

## Лицензия

MIT.
