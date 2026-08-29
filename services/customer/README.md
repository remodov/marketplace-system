# customer

Customer Bounded Context — identity покупателя маркетплейса (регистрация, верификация email, профиль). Часть платформы, описанной в [Marketplace Services Map](https://vikulin-va.ru/case/services-map/).

## Локальный запуск

```bash
docker compose up -d postgres
./gradlew :bootstrap:bootRun --args='--spring.profiles.active=local'
```

Сервис поднимается на `http://localhost:8080`. Health-check:

```bash
curl http://localhost:8080/actuator/health
```

## Сборка и тесты

```bash
./gradlew build           # сборка всех модулей
./gradlew test            # все тесты
./gradlew check           # тесты + checkstyle
```

После изменения схемы БД:

```bash
docker compose up -d postgres
./gradlew :persistence:flywayUpdate   # liquibase update — раскатать миграции
./gradlew :persistence:generateJooq   # пересобрать jOOQ-классы
```

## Профили

| Профиль | Когда | Что |
|---|---|---|
| (default) | production | реальный IdP, Kafka, PG; конфиг через ENV |
| `local` | `./gradlew bootRun` | PG из docker-compose, security `permitAll`, Kafka listeners off |
| `integration-test` | `@SpringBootTest` | testcontainers PG + WireMock, security `permitAll`, schedulers off |

## Структура

Hexagonal multi-module:

```
core/                  — pure Java: domain, aggregate, ports
persistence/           — jOOQ + Liquibase
user-in-adapter/       — REST controllers (Spring Web + OAuth2 Resource Server)
kafka-out-adapter/     — outbox-relay → Kafka
bootstrap/             — composition root, Spring Boot main, configs
migrations/db/         — Liquibase changesets
docs/spec/             — Use Case спецификация (источник правды)
config/                — checkstyle / spotbugs / dep-check suppressions
```

## UCP-методология

См. [CLAUDE.md](CLAUDE.md) — все правки идут через `/ucp-*` скиллы. Спецификация — в [docs/spec/customer-spec.md](docs/spec/customer-spec.md).

## Что почитать

- [Карта сервисов маркетплейса](https://vikulin-va.ru/case/services-map/) — где здесь границы Customer.
- [Keycloak](https://vikulin-va.ru/keycloak/) — выдача токенов и проверка подписи на стороне сервиса.
- [Авторизация и доступ](https://vikulin-va.ru/auth-patterns/) — роли, ABAC, кто что может.
- [Тестирование](https://vikulin-va.ru/testing/) — какие тесты имеют смысл на этом слое.
