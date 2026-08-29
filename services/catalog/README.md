# catalog

Демо-проект Catalog Service из сквозного маркетплейс-кейса сайта [vikulin-va.ru](https://vikulin-va.ru/case/catalog-service/).

**Tier B** (UCP Level 1–2): UseCase Pattern с CQRS-маркерами, без DDD-агрегатов / событий / саг. Простая state machine (`DRAFT → PUBLISHED ↔ HIDDEN`), ABAC в handler'е, persistence — jOOQ + generated only.

Use Case спецификация — [vikulin-va.ru/case/catalog-service/](https://vikulin-va.ru/case/catalog-service/), машинно-читаемая копия в [`docs/spec/`](docs/spec/).

## Зачем этот репо нужен

Это полигон для тренировки [`usecase-pattern-skills`](https://github.com/remodov/usecase-pattern-skills) — каждый шаг (spec-design / ddd-tactical-design / bootstrap-design / pattern-design / auth-design / test-design) прогоняется здесь и фиксируется как «что заработало, что чесалось».

## Локальная разработка

```bash
docker compose up -d postgres
./gradlew regenerate                                            # liquibase update + jOOQ codegen
./gradlew bootRun --args='--spring.profiles.active=local'
```

После старта — `http://localhost:8080/actuator/health`.

### Профили (BS-2)

| Профиль                | Когда                  | Чем отличается                                                           |
| ---------------------- | ---------------------- | ------------------------------------------------------------------------ |
| (без)                  | production / staging   | OAuth2 Resource Server + живой Keycloak (`IDP_JWK_SET_URI`)              |
| `local`                | `bootRun` локально     | Postgres из docker-compose, security `permitAll`, без Kafka              |
| `integration-test`     | `@SpringBootTest`      | Postgres от Testcontainers, security `permitAll`, без Kafka              |

### Регенерация после правки миграций

```bash
./gradlew regenerate    # update + generateJooq
```

`./gradlew update` накатывает Liquibase. `./gradlew generateJooq` генерит POJO/Records/Enums в `build/generated/jooq/main/ru/remodov/catalog/generated/` (в `.gitignore`).

### Запуск миграций отдельно от Gradle

Миграции лежат в `migrations/db/changelog/v-X.Y/` на уровне репо (по `BS-10`) и подцепляются в classpath через `srcDir`. Запускать можно тремя способами:

```bash
# 1. Gradle plugin (для разработчика)
./gradlew update
./gradlew status
./gradlew rollbackCount -PliquibaseCommandValue=1

# 2. Liquibase CLI (для DBA / production / hotfix)
cd migrations && liquibase update
cd migrations && liquibase rollback-count --count=1
cd migrations && liquibase history

# 3. Docker (для CI / без локального Liquibase)
docker run --rm -v "$PWD/migrations:/liquibase/changelog" \
  --network=host liquibase/liquibase:4.30 \
  --defaults-file=/liquibase/changelog/liquibase.properties update
```

`migrations/liquibase.properties` содержит дефолты подключения; переопределяются через `--url=...` или ENV `LIQUIBASE_COMMAND_URL`/`LIQUIBASE_COMMAND_USERNAME`/`LIQUIBASE_COMMAND_PASSWORD`.

## Лицензия

MIT — см. [LICENSE](LICENSE).
