# Backoffice service

Operator Backoffice маркетплейс-кейса. Уровень зрелости — **2** (UseCase + Handler
с CQRS-маркерами, без DDD/Hexagonal). Источник правды по сервису —
[`docs/spec/backoffice-spec.md`](docs/spec/backoffice-spec.md) и
[`docs/spec/aggregates/moderation-action.md`](docs/spec/aggregates/moderation-action.md).

## Стек

Java 21, Spring Boot 3.4.2, single-module Gradle (Kotlin DSL), `ru.remodov.backoffice.*`.
Persistence — **только jOOQ, только generated** из applied Liquibase-схемы (BS-17/18).
REST — OpenAPI-generator (server-stubs); исходящий клиент к Catalog —
spring-restclient (добавляется скиллом `ucp-integration-design`). Resilience4j
для Circuit Breaker / Retry / TimeLimiter на Catalog-вызовах.

## Quickstart

```bash
docker compose up -d postgres
./gradlew regenerate        # liquibase update + jOOQ codegen
./gradlew bootRun --args='--spring.profiles.active=local'
# → Tomcat started on port 8080
```

## Тесты

```bash
docker compose up -d postgres     # обязательно — тесты идут на localhost:5433
./gradlew test
```

Интеграционные тесты используют существующий PostgreSQL из `docker-compose.yml`
(не Testcontainers), потому что в Claude-Code-окружении Docker socket возвращает
ограниченные ответы, и Testcontainers не может сконфигурироваться. На обычной
dev-машине / в CI можно вернуть `@Testcontainers` с `PostgreSQLContainer` (см.
git history `BackofficeIntegrationTest.java` — первая итерация).

WireMock крутится на фиксированном порту 9561 (`application-integration-test.yml`).

Профили (BS-2):

| Профиль | Когда | Security | Catalog URL |
|---|---|---|---|
| (нет) | production | OAuth2 RS / JWT через Keycloak | `${CATALOG_BASE_URL}` |
| `local` | `bootRun` локально | `permitAll` | `http://localhost:8081` |
| `integration-test` | `@SpringBootTest` | `permitAll`, OAuth2 autoconfig excluded | `http://localhost:9561` (WireMock) |

## Регенерация jOOQ после миграций

```bash
./gradlew update            # liquibase update
./gradlew generateJooq      # codegen из applied-схемы
# либо одной командой:
./gradlew regenerate
```

Сгенерированные классы (`ru.remodov.backoffice.generated.*`) под `build/generated/`,
в VCS не уходят. Не редактируй их — будут перезатёрты (BS-19).

## Структура

```
backoffice/
├── docs/spec/                   # источник правды (Use Case спека)
├── migrations/db/changelog/     # Liquibase changesets (BS-10)
│   └── v-1.0/                   # initial schema + enum types
├── src/main/java/ru/remodov/backoffice/
│   ├── BackofficeApplication.java
│   ├── config/                  # ServiceBeansConfig, Security, Jackson
│   └── core/service/            # DateTimeService, UuidGenerator (тестируются через @MockitoBean)
├── src/main/resources/
│   ├── application.yml          # production-defaults
│   ├── application-local.yml    # overrides для bootRun
│   ├── application-integration-test.yml
│   └── openapi/backoffice.openapi.yaml
├── build.gradle.kts             # jOOQ codegen + OpenAPI-generator + Resilience4j
└── docker-compose.yml           # PostgreSQL 16 на 5433
```

Порт Postgres — **5433** (а не 5432), чтобы можно было одновременно поднимать
Catalog (5432) и Backoffice локально.

<!-- BEGIN ucp-skills (managed by claude-code-java/install.sh) -->
## Use Case Pattern skills

Проект использует методологию Use Case Pattern. Скиллы `ucp-*` и style-guides
подключены через `install.sh` из репо скиллов (полная справка — в его README).

### Язык общения

Все ответы Claude в чате — **на русском**: планы `superpowers:writing-plans` и их
шаги, описания PR / коммит-сообщения, end-of-turn summary, комментарии в коде
(их по стилю мало — см. `.claude/docs/java-style-guide.md`).

**Технические термины и идентификаторы — на английском, как в коде:** `UseCase`,
`Handler`, `Controller`, `Aggregate`, `Bounded Context`, `Domain Event`,
`Repository`, `REST`, `Kafka`, имена классов / методов / переменных / SQL-таблиц.
Не транслитерируем.

Если пользователь пишет на английском или просит ответ на английском — отвечаем
на английском. По умолчанию — русский.

### Спецификация

Если в проекте есть `docs/spec/` — это источник правды по сервису (формат — Use Case
спецификация Bounded Context). Точка входа — корневой файл контекста
`docs/spec/<service>-spec.md` (секции уровня контекста); домен-юниты — в
`docs/spec/aggregates/<name>.md` (всегда отдельным файлом, даже один).

**Читай корневой файл в начале сессии, не в конце** — позднее чтение churn-ит
prompt-кэш и удорожает каждый ход; раннее чтение оседает в тёплом префиксе.
Опционально: `@docs/spec/<service>-spec.md` **снаружи** managed-блока авто-грузит
корень в каждый контекст (не для тяжёлых спек — раздувает always-loaded).

Ссылки между разделами — по именам/якорям (`[Жизненный цикл](#2-жизненный-цикл)`);
машинная идентичность чанков — минимальный frontmatter (`context`, `aggregate`, `level`).
Техника (схема БД, стек, топики) — только в разделе «Техническая реализация»; домен —
во всех остальных разделах.

### Кодогенерация и ревью — через скиллы

Любая работа над UCP-артефактом **обязана** идти через соответствующий `/ucp-*`
скилл, не писаться от руки — иначе код дрейфует от методологии и review-скиллы
ругаются.

| Тип работы | Скилл |
|---|---|
| Написать спеку по бизнес-описанию | `/ucp-spec-design` |
| As-is спека из кода (онбординг) | `/ucp-spec-tier-0` |
| Ревью спеки (design-критик) | `/ucp-spec-review` |
| UseCase + Handler + Controller + маппер | `/ucp-pattern-design` |
| OpenAPI + DTO + контроллер из карточки команды | `/ucp-api-design` |
| Spring Security + OAuth2 + ABAC + audit | `/ucp-auth-design` |
| Spring Boot bootstrap, Liquibase, jOOQ, профили (+ корневой CLAUDE.md) | `/ucp-bootstrap-design` |
| Aggregate + VO + Domain Event + Repository (Уровень 3) | `/ucp-ddd-tactical-design` |
| Тесты по разделу «Критерии приёмки» | `/ucp-test-design` |
| Ревью UseCase / Handler / Controller | `/ucp-pattern-review` |
| Ревью REST-контракта | `/ucp-api-review` |
| Ревью DDD-кода | `/ucp-ddd-tactical-review` |
| Ревью Java-стиля (что не ловит checkstyle) | `/ucp-java-style-review` |
| Ревью авторизации | `/ucp-auth-review` |
| Ревью тестов (структура, покрытие UC/BR, slow/flaky) | `/ucp-test-review` |
| Ревью outbound-интеграции (структура, port, mapper) | `/ucp-integration-review` |

(Полный список design↔review-пар — в README репо скиллов.) Style-guides — в
`.claude/docs/*.md`. **Читай нужную секцию гайда, не весь файл**: гайды большие,
у каждого правила есть код (`R-API-*`, `R-JOOQ-*`, `JS-*`, …) — навигируйся по
кодам и заголовкам, full-read только когда реально нужен весь документ.

### Триггеры и цепочки

Фразы **«сделай сервис», «пишем сервис», «новый сервис», «напиши сервис», «начнём сервис», «сгенерим сервис»** — это триггер цепочки скиллов, не запрос на свободное проектирование. **Запусти `/ucp-new-service`** — он оркеструет всю последовательность.

Если нужно запустить цепочку вручную (например, продолжая с середины):

| Шаг | Скилл | Когда пропустить |
|---|---|---|
| 1. Спека | `/ucp-spec-design` | если `docs/spec/` уже есть |
| 2. DDD-слой | `/ucp-ddd-tactical-design` | Уровень 1 или 2 |
| 3. Bootstrap | `/ucp-bootstrap-design` | если скелет уже есть |
| 4. UseCase / Handler / Controller | `/ucp-pattern-design` | — |
| 5. Авторизация | `/ucp-auth-design` | если нет ролей в спеке |
| 6. Тесты | `/ucp-test-design` | — |

**Если `ucp-*` скиллы не видны в available-skills** — сессия запущена не из директории сервиса. Немедленно скажи «перезапусти Claude из `/path/to/service`», не задавай вопросов про архитектуру и не изобретай структуру.

`install.sh` ставит три hook'а в `.claude/settings.json`, которые делают эту логику детерминированной (срабатывают на стороне harness, до модели):
- **UserPromptSubmit** (`ucp-trigger-detect.sh`) — детектит триггер-фразы и инжектит инструкцию запустить `/ucp-new-service`.
- **SessionStart** (`ucp-session-check.sh`) — проверяет, что симлинки `ucp-*` не broken; если broken — инжектит warning с командой починки.
- **PostToolUse** на `Skill` (`ucp-post-skill-review.sh`) — после `ucp-pg-schema-design` / `ucp-pg-migration-design` напоминает запустить парный review (для DDL/миграций ревью обязателен).

### Интеграция с superpowers

Если задача планируется/исполняется через `superpowers:writing-plans` /
`executing-plans` — **шаги плана называют конкретные `/ucp-*` скиллы**, не прозу
(«вызвать `/ucp-pattern-design` для CreateProduct», не «реализовать CreateProduct»);
шаг ревью — отдельной парой к design-шагу; тесты — через `/ucp-test-design`. При
исполнении: если шаг ссылается на ucp-скилл — вызвать его, не пересказывать
словами. Без этого superpowers строит размытые планы в обход методологии.

Авто-ревью **сразу после** design-скилла — по запросу, не по умолчанию: код,
сгенерированный design-скиллом по чек-листу, уже соответствует правилам.
Обязательный review — для рукописного/изменённого кода и **всегда** для DDL и
миграций (`ucp-pg-schema-review` / `ucp-pg-migration-review`).

### Структура плана — вертикальные срезы, MVP-first

План реализации **режется на вертикальные срезы**, не на горизонтальные слои.
Каждая фаза — **работающий end-to-end кусок**: его можно собрать, запустить,
протестировать. Не «фаза 1: все DTO, фаза 2: все контроллеры, фаза 3: все
хендлеры» — а «фаза 1: одна команда `CreateOrder` целиком (DTO → контроллер →
хендлер → repository → миграция → тест), фаза 2: следующая команда, …».

Почему: горизонтальные слои нельзя протестировать до последней фазы — баг в
дизайне всплывает поздно и стоит дорого. Вертикальный срез даёт обратную связь
после **каждой** фазы: собрался → прошли тесты → ревью → следующий срез. Это
и есть MVP-first: первый срез — минимальный сквозной путь, дальше — наращивание.

При планировании через `superpowers:writing-plans`: фаза = `/ucp-*` цепочка
для одной команды/запроса (`ucp-api-design` → `ucp-pattern-design` →
`ucp-test-design` → review), законченная и проверяемая. При исполнении —
не начинать следующую фазу, пока текущая не собирается и не зелёная по тестам.

Исключение: чисто-инфраструктурный bootstrap (Spring Boot skeleton, Liquibase,
jOOQ codegen, профили) — это фаза 0, она по природе горизонтальная и одна.
После неё — только вертикальные срезы.

### Skill vs Agent

`/ucp-*` скиллы вызываются через **`Skill` tool в текущей сессии** (или
slash-командой `/ucp-...`) — переиспользуется прогретый кэш и уже загруженный
style-guide. **`Agent` / `Task`-форк под ucp-скилл запрещён**, в т.ч. для «параллельного»
прогона нескольких ревью: форк стартует с холодным кэшем, перечитывает гайд
заново, форки не видят результатов друг друга — fan-in N ревью через Agent ≈ N×
токенов последовательного `Skill` без выигрыша по качеству. Исключение — форк
нужен по другой причине (изолированный worktree, длинный сторонний output): это
осознанный trade-off, не default.

### Параллельные субагенты — когда да, когда нет

| Ситуация | Параллельно? |
|---|---|
| Тесты + линт + build одновременно | да |
| Fan-out однотипных правок (одинаковая замена в 30 файлах) | да |
| N ucp-ревью на одном PR | **нет** — последовательно через `Skill` |
| Сгенерировать N агрегатов/команд/событий из спеки | нет |
| Цепочка `ucp-spec-design` → `ucp-pattern-design` → `ucp-test-design` | нет |
| Сквозной рефакторинг по нескольким файлам | нет |

Параллельный design запрещён: субагенты не видят прогресс друг друга, контекст
не общий — артефакты разойдутся по стилю и именам (ровно та проблема, против
которой UCP).

### Bootstrap и управление блоком

Если `.claude/skills/` пуст или в нём broken-симлинки — скиллы не установлены:
`git pull` в репо скиллов и `<repo>/install.sh .` (детали — README репо).
Всё между маркерами `BEGIN ucp-skills` / `END ucp-skills` управляется `install.sh`
и перезаписывается; проект-специфичные инструкции для Claude пишите **снаружи**
маркеров — они сохранятся.
<!-- END ucp-skills -->
