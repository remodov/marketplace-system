---
type: context-section
context: order-service
parent: "[[order-service]]"
section: stack
tier: C
ucp-level: 3
tags:
  - stack
  - tech/java
  - tech/spring-boot
  - tech/jooq
  - tech/postgres
  - tech/kafka
  - tech/redis
  - tech/ddd
  - bc/order
---

## 17. Стек технологий

### Платформа

- **Java 21** — records, sealed, pattern matching.
- **Spring Boot 3.x** — DI, web, observability.
- **Gradle (Kotlin DSL)** — сборка.

### Use Case Pattern

- **`ru.vikulinva:usecase-pattern-starter`** — `UseCase`/`UseCaseHandler`/`UseCaseDispatcher` + auto-config + Micrometer метрики на каждый use case.
- **CQRS-маркеры** `UseCaseCommand`/`UseCaseQuery` (из той же библиотеки).

### DDD

- **`ru.vikulinva:ddd-building-blocks`** — `AggregateRoot`/`Entity`/`ValueObject`/`DomainEvent`/`AggregateRepository`/`Specification`.

### Хранилище

- **PostgreSQL 16+** — основное хранилище (write-side + Read Model + Outbox).
- **jOOQ 3.19+** — типобезопасный SQL, генерация Pojo из схемы.
- **Flyway** — миграции.
- **HikariCP** — connection pool.

### События

- **Apache Kafka 3.x** — транспорт между сервисами, консьюмер-группы, key-based partitioning.
- **Spring Kafka** — продюсер и консьюмер.
- **Outbox-relay** — Debezium + Kafka Connect (или своя `@Scheduled`-job в первой версии).

### Кэш

- **Redis** — `@Cacheable` для `SearchSellerOrdersQuery` (TTL 30s), JWK Set от Keycloak (TTL 5 мин), идемпотентные ключи Catalog-валидации.

### Маппинг

- **MapStruct 1.5+** — JsonBean ↔ доменная модель ↔ jOOQ Pojo. Генерируется на этапе компиляции, без рефлексии.
- **Lombok** — boilerplate (опционально, может быть убран).

### Resilience

- **Resilience4j** — Retry / Circuit Breaker / Timeout / Bulkhead / Fallback на адаптерах вызовов Catalog, Payment, внешних логистов.

### Безопасность

- **Spring Security** + **Spring Security OAuth2 Resource Server** — валидация JWT.
- **Keycloak** — IdP (внешний, не часть Order Service).
- **mTLS** — на внутренних REST-вызовах.

### Наблюдаемость

- **Micrometer** + **Prometheus** — метрики.
- **OpenTelemetry** → **Tempo** — трассировка.
- **Loki** — структурные логи (JSON).
- **Grafana** — дашборды.

### Тесты

- **JUnit 5** — unit и integration.
- **AssertJ** — assertions.
- **Mockito** — моки в unit-тестах.
- **Testcontainers** — integration: PostgreSQL, Kafka, Redis.
- **WireMock** — моки внешних REST (Catalog, Payment) в integration.
- **Gatling** — нагрузочные тесты.
- **ArchUnit** — архитектурные правила (Tier C/L4 — не сейчас, в L4-сервисе).

### Инфраструктура

- **Docker** — контейнеризация.
- **Kubernetes** + **Helm chart** — деплой.
- **GitHub Actions** — CI/CD (build, test, image push, deploy).

### Скиллы AI-агента

- `usecase-spec-design` — пишет/обновляет эту спеку.
- `ddd-tactical-design` — генерирует код агрегата `Order`, событий, repository.
- `ddd-tactical-review` — ревью доменного кода на соответствие правилам.
- `usecase-pattern-design` — генерирует UseCase + Handler.
- `usecase-pattern-review` — ревью на соответствие методологии.
- `api-design` — OpenAPI из раздела Commands.
- `api-review` — ревью контракта.

Скиллы лежат в `github.com/remodov/usecase-pattern-skills`. Подключаются симлинком в `.claude/skills/`.
