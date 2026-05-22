# Notification Service

## Спецификация

`docs/spec/notification-service-spec.md` — источник правды (формат — Use Case спецификация Bounded Context; Tier A). Корень — контекст-секции; домен-юнит Notification — в `docs/spec/aggregates/notification.md`. **Читай в начале сессии** — раннее чтение оседает в тёплом префиксе кэша. Техника (схема БД, стек, топики) — в разделе «Техническая реализация»; домен — в остальных разделах; ссылки между разделами — по именам/якорям.

## Кодогенерация и ревью — через скиллы

Любая работа над UCP-артефактом — через соответствующий `/ucp-*` скилл, не от руки.

| Тип работы | Скилл |
|---|---|
| Спека по бизнес-описанию / из кода / ревью | `/ucp-spec-design` · `/ucp-spec-tier-0` · `/ucp-spec-review` |
| Код Tier A (контроллеры, сервисы, jOOQ, шедулеры) | `/ucp-pattern-design`, `/ucp-bootstrap-design` |
| OpenAPI + DTO | `/ucp-api-design` |
| Тесты по разделу «Критерии приёмки» | `/ucp-test-design` |

Notification — **Tier A** (слоёная архитектура, без UseCase Pattern и DDD): агрегатов, доменных событий и саг нет. Persistence — только jOOQ на сгенерированных классах (`BS-17`), как на любом Tier.
