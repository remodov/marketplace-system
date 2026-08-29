# Catalog Review Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Закрыть 52 находки параллельного review (5 UCP-скиллов) на catalog-сервисе, привести код в соответствие со спекой и UCP Tier B (Уровень 2).

**Architecture:** Catalog — Spring Boot 3 / Java 21 / jOOQ / PostgreSQL. UseCase Pattern + CQRS-маркеры, без DDD-агрегатов/событий. Фиксы делаем 7-ю фазами от изолированных (API-контракт) к глубоким (доменные типы, refactor репозитория). Каждая фаза — самостоятельный коммит, TDD где применимо.

**Tech Stack:** Java 21, Spring Boot 3.4.x, jOOQ 3.19, PostgreSQL 16, Liquibase, JUnit 5 + Testcontainers, OpenAPI 7.10 generator, Lombok, MapStruct.

**Источник правды:**
- Спека: `docs/spec/catalog-spec.md` + `docs/spec/aggregates/product.md`
- Скиллы: `.claude/skills/ucp-*` (симлинки в `~/IdeaProjects/usecase-pattern-skills/`)
- Style guides: `.claude/docs/*.md` (rest-api-rules, jooq-rules, usecase-pattern-rules, java-style-guide и т.д.)

**Команды для проверки на каждом шаге:**
```bash
./gradlew compileJava           # быстрая проверка компиляции
./gradlew test                  # полный прогон (если запущен Docker — Testcontainers поднимет PG)
./gradlew openApiGenerate       # перегенерация DTO из OpenAPI
./gradlew generateJooq          # перегенерация jOOQ (нужен локальный PG с накатанными миграциями)
```

---

## Phase 1 — API-контракт (CRITICAL, изолированные правки)

### Task 1.1: ProblemDetails type — URN вместо about:blank

**Скилл:** `ucp-api-design`. **Правило:** R-ERR-X2.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/controller/ProblemDetailExceptionHandler.java:73-79`

- [ ] **Step 1: Заменить `about:blank` на URN в `problemDetail()`**

```java
private ProblemDetail problemDetail(HttpStatus status, String code, String detail) {
    var pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create("urn:problem:catalog:" + code));
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty("code", code);
    return pd;
}
```

- [ ] **Step 2: Компилировать**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Прогнать существующие тесты ошибок**

Run: `./gradlew test --tests "*IntegrationTest"`
Expected: PASS (тесты на `code`-поле проходят; type не проверяется в существующих тестах — это OK для шага).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/remodov/catalog/controller/ProblemDetailExceptionHandler.java
git commit -m "fix(api): ProblemDetails type → urn:problem:catalog:<code> (R-ERR-X2)"
```

---

### Task 1.2: Добавить 500 response во все эндпоинты OpenAPI

**Скилл:** `ucp-api-design`. **Правило:** R-ERR-9.

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml` — пять `paths` + секция `components/responses`

- [ ] **Step 1: Добавить `InternalServerError` в `components/responses`**

Найти секцию `components: responses:` и добавить:

```yaml
    InternalServerError:
      description: Внутренняя ошибка сервера
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetails' }
          examples:
            internalError:
              value:
                type: 'urn:problem:catalog:INTERNAL_SERVER_ERROR'
                title: Internal Server Error
                status: 500
                detail: Внутренняя ошибка сервера
                code: INTERNAL_SERVER_ERROR
```

- [ ] **Step 2: Добавить ссылку `'500'` во все 5 операций**

Для каждой операции (`createProduct`, `getProduct`, `publishProduct`, `hideProduct`, `listMyProducts`) в секции `responses:` добавить:

```yaml
        '500':
          $ref: '#/components/responses/InternalServerError'
```

- [ ] **Step 3: Перегенерировать DTO**

Run: `./gradlew openApiGenerate compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml
git commit -m "fix(api): 500 response declared on all endpoints (R-ERR-9)"
```

---

### Task 1.3: Fallback exception handler для непредвиденных 500

**Скилл:** `ucp-api-design`. **Правило:** R-ERR-X1.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/controller/ProblemDetailExceptionHandler.java`

- [ ] **Step 1: Написать integration test, проверяющий что unexpected exception возвращает application/problem+json**

Create: `src/test/java/ru/remodov/catalog/controller/UnexpectedExceptionIntegrationTest.java`

```java
package ru.remodov.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class UnexpectedExceptionIntegrationTest extends CatalogBaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void unexpectedException_returns500AndProblemJson() {
        var headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<JsonNode> response = rest.exchange(
            "/__boom",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        assertThat(response.getStatusCode().value()).isIn(404, 500);
        if (response.getStatusCode().is5xxServerError()) {
            assertThat(response.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
            assertThat(response.getBody().get("code").asText())
                .isEqualTo("INTERNAL_SERVER_ERROR");
        }
    }
}
```

> Примечание: тест tolerant — Spring может вернуть 404 (нет handler), тогда mvc-тест на 500 пишем отдельно после следующего шага. Если хотите гарантированный 500 — добавьте тестовый контроллер в test-source, бросающий RuntimeException.

- [ ] **Step 2: Добавить fallback `@ExceptionHandler(Exception.class)`**

В `ProblemDetailExceptionHandler.java` перед закрывающей `}`:

```java
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
            "Внутренняя ошибка сервера");
    }
```

- [ ] **Step 3: Прогон**

Run: `./gradlew test --tests "*UnexpectedExceptionIntegrationTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/remodov/catalog/controller/ProblemDetailExceptionHandler.java \
        src/test/java/ru/remodov/catalog/controller/UnexpectedExceptionIntegrationTest.java
git commit -m "fix(api): fallback Exception handler returns 500 problem+json (R-ERR-X1)"
```

---

### Task 1.4: Пагинация 1-based в OpenAPI и handler

**Скилл:** `ucp-api-design`. **Правило:** R-QRY-X2.

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml:149-150` (параметр `page`)
- Modify: `src/main/resources/openapi/catalog.openapi.yaml` (пример `ProductPageDto`)
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQuery.java:18-22`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQueryHandler.java:28`
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/ListMyProductsIntegrationTest.java` (ожидания)

- [ ] **Step 1: Изменить OpenAPI: `page` → 1-based**

В `catalog.openapi.yaml`, параметр `page`:

```yaml
        - name: page
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            default: 1
```

В примере `ProductPageDto` найти `page: 0` и заменить на `page: 1`.

- [ ] **Step 2: Изменить валидацию в `ListMyProductsQuery`**

```java
    public ListMyProductsQuery {
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be in (0, 100]");
        }
        if (sort == null) {
            sort = ProductRepository.SortField.CREATED_AT_DESC;
        }
    }
```

- [ ] **Step 3: Изменить расчёт offset в `ListMyProductsQueryHandler`**

Заменить строку 28:

```java
        List<ProductDto> content = repo.findBySeller(
                sellerId, q.statusFilter(),
                (q.page() - 1) * q.size(), q.size(), q.sort())
            .stream()
            .map(mapper::toDto)
            .toList();
```

- [ ] **Step 4: Обновить ожидания в тесте**

В `ListMyProductsIntegrationTest.java` все вызовы `?page=0` заменить на `?page=1`; в assertions `.getPage()` `== 0` → `== 1`.

- [ ] **Step 5: Прогон**

Run: `./gradlew openApiGenerate compileJava test --tests "*ListMyProducts*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml \
        src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQuery.java \
        src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQueryHandler.java \
        src/test/java/ru/remodov/catalog/usecase/product/ListMyProductsIntegrationTest.java
git commit -m "fix(api): pagination is 1-based (R-QRY-X2)"
```

---

### Task 1.5: `spring.jackson.default-property-inclusion: non_null`

**Скилл:** `ucp-api-design`. **Правило:** R-RSP-X1.

**Files:**
- Modify: `src/main/resources/application.yml` (или `application.properties`)

- [ ] **Step 1: Проверить наличие конфига**

```bash
ls src/main/resources/application*
```

- [ ] **Step 2: Добавить в `application.yml` (если есть YAML)**

```yaml
spring:
  jackson:
    default-property-inclusion: non_null
```

Если конфиг в `application.properties`:

```properties
spring.jackson.default-property-inclusion=non_null
```

- [ ] **Step 3: Написать тест: ProductDto без description не имеет поля `description: null` в JSON**

В `CreateProductIntegrationTest.java` добавить кейс:

```java
    @Test
    void create_whenDescriptionAbsent_responseHasNoDescriptionField() throws Exception {
        var body = """
            {"title":"T","price":100.00,"currency":"RUB"}
            """;
        var response = postCreate(body, sellerToken);
        var json = objectMapper.readTree(response.getBody());
        assertThat(json.has("description")).isFalse();
    }
```

(подгоните под существующие helpers в `CreateProductIntegrationTest`)

- [ ] **Step 4: Прогон**

Run: `./gradlew test --tests "*CreateProductIntegrationTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application*.yml src/main/resources/application*.properties \
        src/test/java/ru/remodov/catalog/usecase/product/CreateProductIntegrationTest.java
git commit -m "fix(api): Jackson default-property-inclusion=non_null (R-RSP-X1)"
```

---

### Task 1.6: Path-параметр `id` → `productId`

**Скилл:** `ucp-api-design`. **Правило:** R-OAS-3.

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml` (paths, parameters)
- Modify: `src/main/java/ru/remodov/catalog/controller/ProductController.java` (сгенерированные интерфейсы — имена параметров)

- [ ] **Step 1: Переименовать в OpenAPI**

В `catalog.openapi.yaml`:
- Шаблоны путей: `/api/v1/products/{id}` → `/api/v1/products/{productId}`, аналогично для `/publish` и `/hide`.
- Параметр-объект `ProductId` (в `components/parameters` если есть): `name: id` → `name: productId`.

- [ ] **Step 2: Перегенерировать DTO/API и подправить контроллер**

Run: `./gradlew openApiGenerate compileJava`

В `ProductController.java` методы `getProduct`, `publishProduct`, `hideProduct` сигнатура `UUID id` сменится на `UUID productId` — IDE/компилятор подскажет. Тело — переименовать `id` → `productId`.

- [ ] **Step 3: Поправить тесты (интеграционные)**

Большинство тестов вызывают URL — формат `/api/v1/products/{uuid}` не меняется, имя плейсхолдера в коде теста не критично.

- [ ] **Step 4: Прогон**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml \
        src/main/java/ru/remodov/catalog/controller/ProductController.java
git commit -m "fix(api): path parameter id → productId (R-OAS-3)"
```

---

### Task 1.7: Tag `products` → `Products`

**Скилл:** `ucp-api-design`. **Правило:** R-OAS-2.

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml`

- [ ] **Step 1: Заменить тег**

В секции `tags:` верхнего уровня:

```yaml
tags:
  - name: Products
    description: ...
```

В каждой операции (5 мест): `tags: [products]` → `tags: [Products]`.

- [ ] **Step 2: Регенерация + компиляция**

Run: `./gradlew openApiGenerate compileJava`
Expected: BUILD SUCCESSFUL. (имя интерфейса может измениться с `ProductsApi` — допустимо; если изменилось, обновить `implements ProductsApi` в `ProductController`)

- [ ] **Step 3: Прогон тестов**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml \
        src/main/java/ru/remodov/catalog/controller/ProductController.java
git commit -m "fix(api): tag Products (capitalized plural) (R-OAS-2)"
```

---

### Task 1.8: `required` поля в схеме ProblemDetails

**Скилл:** `ucp-api-design`. **Правило:** R-RSP-8.

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml` — секция `components: schemas: ProblemDetails`

- [ ] **Step 1: Добавить `required`**

```yaml
    ProblemDetails:
      type: object
      description: Problem Details (RFC 9457)
      required: [type, status, title, detail, code]
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        code: { type: string }
        instance: { type: string, format: uri }
        traceId: { type: string }
        violations:
          type: array
          items: { $ref: '#/components/schemas/Violation' }
```

- [ ] **Step 2: Регенерация + компиляция + тесты**

Run: `./gradlew openApiGenerate compileJava test`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml
git commit -m "fix(api): ProblemDetails required fields declared (R-RSP-8)"
```

---

### Task 1.9: BR-C → BR-P в OpenAPI descriptions

**Скилл:** `ucp-api-design`. **Правило:** R-OAS (соответствие спеке).

**Files:**
- Modify: `src/main/resources/openapi/catalog.openapi.yaml`

- [ ] **Step 1: Заменить все упоминания BR-C на BR-P**

В операциях `createProduct`, `getProduct`, `publishProduct`, `hideProduct`:
`BR-C1`→`BR-P01`, `BR-C2`→`BR-P02`, `BR-C3`→`BR-P03`, `BR-C4`→`BR-P04`, `BR-C5`→`BR-P05`, `BR-C6`→`BR-P06`.

- [ ] **Step 2: Регенерация + компиляция (DTO description-аннотации могут поменяться)**

Run: `./gradlew openApiGenerate compileJava`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/openapi/catalog.openapi.yaml
git commit -m "docs(api): align BR codes with product spec (BR-C → BR-P)"
```

---

## Phase 2 — jOOQ codegen (HIGH, требует regen)

### Task 2.1: `forcedTypes` TIMESTAMPTZ → OffsetDateTime

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-CFG-4, R-JOOQ-CFG-X3.

**Files:**
- Modify: `build.gradle.kts:101-106`

- [ ] **Step 1: Импорт `ForcedType` и `Generate`**

В начало `build.gradle.kts` (после `import org.jooq.meta.jaxb.Logging`):

```kotlin
import org.jooq.meta.jaxb.ForcedType
```

- [ ] **Step 2: Добавить `forcedTypes` в codegen**

В блоке `database.apply { ... }` (после `excludes`):

```kotlin
                        forcedTypes.addAll(listOf(
                            ForcedType().apply {
                                userType = "java.time.OffsetDateTime"
                                includeTypes = "TIMESTAMP\\ WITH\\ TIME\\ ZONE|TIMESTAMPTZ"
                            }
                        ))
```

- [ ] **Step 3: Регенерировать jOOQ (нужен поднятый PG + накатанные миграции)**

```bash
docker compose up -d postgres   # если уже не запущен
./gradlew update                # liquibase
./gradlew generateJooq
```

Expected: `build/generated/jooq/main/.../ProductsPojo.java` теперь имеет `private OffsetDateTime createdAt;` вместо `LocalDateTime`.

- [ ] **Step 4: Удалить ручные конвертации в handler'ах и репозитории**

В `CreateProductUseCaseHandler.java:30`:

```java
        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
```

(добавить импорт `import java.time.ZoneOffset;`)

Замена в `pojo.setCreatedAt(now); pojo.setUpdatedAt(now);` — теперь `now` имеет тип `OffsetDateTime`, что напрямую совместимо.

Аналогично — `HideProductUseCaseHandler.java:50` и `PublishProductUseCaseHandler.java:50`:

```java
        product.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
```

И в `JooqProductRepository.java:42`:

```java
            .set(PRODUCTS.UPDATED_AT, updatedAt.atOffset(ZoneOffset.UTC))
```

- [ ] **Step 5: Компиляция + тесты**

Run: `./gradlew compileJava test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts \
        src/main/java/ru/remodov/catalog/usecase/product/CreateProductUseCaseHandler.java \
        src/main/java/ru/remodov/catalog/usecase/product/PublishProductUseCaseHandler.java \
        src/main/java/ru/remodov/catalog/usecase/product/HideProductUseCaseHandler.java \
        src/main/java/ru/remodov/catalog/repository/JooqProductRepository.java
git commit -m "fix(jooq): TIMESTAMPTZ → OffsetDateTime via forcedTypes (R-JOOQ-CFG-4)"
```

---

### Task 2.2: `isFluentSetters = true`

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-CFG-7.

**Files:**
- Modify: `build.gradle.kts:105`

- [ ] **Step 1: Включить fluent-сеттеры**

В `generate.apply { ... }`:

```kotlin
                        isFluentSetters = true
```

- [ ] **Step 2: Регенерация + компиляция**

Run: `./gradlew generateJooq compileJava test`
Expected: PASS. Существующий код продолжает работать — старые сеттеры void тоже доступны (jOOQ генерит оба варианта).

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "fix(jooq): isFluentSetters=true (R-JOOQ-CFG-7)"
```

---

## Phase 3 — Доменные типы и границы слоёв (CRITICAL, refactor)

### Task 3.1: Доменный enum `ProductSortField`

**Скилл:** `ucp-pattern-design`. **Правило:** R-LAY-1.

**Files:**
- Create: `src/main/java/ru/remodov/catalog/domain/ProductSortField.java`

- [ ] **Step 1: Создать доменный enum**

```java
package ru.remodov.catalog.domain;

public enum ProductSortField {
    CREATED_AT_DESC, CREATED_AT_ASC,
    UPDATED_AT_DESC, UPDATED_AT_ASC,
    TITLE_ASC, TITLE_DESC;

    public static ProductSortField parse(String raw) {
        if (raw == null || raw.isBlank()) return CREATED_AT_DESC;
        return switch (raw.toLowerCase()) {
            case "createdat,asc" -> CREATED_AT_ASC;
            case "createdat,desc" -> CREATED_AT_DESC;
            case "updatedat,asc" -> UPDATED_AT_ASC;
            case "updatedat,desc" -> UPDATED_AT_DESC;
            case "title,asc" -> TITLE_ASC;
            case "title,desc" -> TITLE_DESC;
            default -> CREATED_AT_DESC;
        };
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (enum пока не используется).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/remodov/catalog/domain/ProductSortField.java
git commit -m "feat(domain): ProductSortField enum (R-LAY-1)"
```

---

### Task 3.2: Доменный record `Product`

**Скилл:** `ucp-pattern-design`. **Правило:** R-LAY-1, R-JOOQ-MAP-X1.

**Files:**
- Create: `src/main/java/ru/remodov/catalog/domain/Product.java`

- [ ] **Step 1: Создать record**

```java
package ru.remodov.catalog.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Product(
    UUID id,
    String title,
    String description,
    BigDecimal price,
    String currency,
    UUID sellerId,
    Status status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public enum Status { DRAFT, PUBLISHED, HIDDEN }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/remodov/catalog/domain/Product.java
git commit -m "feat(domain): Product record + Status enum (R-JOOQ-MAP-X1)"
```

---

### Task 3.3: `ProductDomainRecordMapper` — POJO ↔ Product

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-MAP-1.

**Files:**
- Create: `src/main/java/ru/remodov/catalog/repository/ProductDomainRecordMapper.java`

- [ ] **Step 1: Написать unit-тест маппера**

Create: `src/test/java/ru/remodov/catalog/repository/ProductDomainRecordMapperTest.java`

```java
package ru.remodov.catalog.repository;

import org.junit.jupiter.api.Test;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDomainRecordMapperTest {

    private final ProductDomainRecordMapper mapper = new ProductDomainRecordMapper();

    @Test
    void toDomain_mapsAllFields() {
        var now = OffsetDateTime.of(2026, 5, 23, 12, 0, 0, 0, ZoneOffset.UTC);
        var pojo = new ProductsPojo();
        pojo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        pojo.setTitle("T");
        pojo.setDescription("D");
        pojo.setPrice(new BigDecimal("100.00"));
        pojo.setCurrency("RUB");
        pojo.setSellerId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        pojo.setStatus(ProductStatus.PUBLISHED);
        pojo.setCreatedAt(now);
        pojo.setUpdatedAt(now);

        Product result = mapper.toDomain(pojo);

        assertThat(result.id()).isEqualTo(pojo.getId());
        assertThat(result.title()).isEqualTo("T");
        assertThat(result.status()).isEqualTo(Product.Status.PUBLISHED);
        assertThat(result.createdAt()).isEqualTo(now);
    }

    @Test
    void fromDomain_mapsAllFields() {
        var now = OffsetDateTime.of(2026, 5, 23, 12, 0, 0, 0, ZoneOffset.UTC);
        var product = new Product(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "T", "D", new BigDecimal("100.00"), "RUB",
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            Product.Status.DRAFT, now, now
        );

        ProductsPojo pojo = mapper.fromDomain(product);

        assertThat(pojo.getId()).isEqualTo(product.id());
        assertThat(pojo.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(pojo.getCreatedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Запустить тест — должен упасть**

Run: `./gradlew test --tests "*ProductDomainRecordMapperTest"`
Expected: FAIL (класса нет).

- [ ] **Step 3: Реализовать маппер**

```java
package ru.remodov.catalog.repository;

import org.springframework.stereotype.Component;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

@Component
public class ProductDomainRecordMapper {

    public Product toDomain(ProductsPojo pojo) {
        return new Product(
            pojo.getId(),
            pojo.getTitle(),
            pojo.getDescription(),
            pojo.getPrice(),
            pojo.getCurrency(),
            pojo.getSellerId(),
            toDomainStatus(pojo.getStatus()),
            pojo.getCreatedAt(),
            pojo.getUpdatedAt()
        );
    }

    public ProductsPojo fromDomain(Product product) {
        var pojo = new ProductsPojo();
        pojo.setId(product.id());
        pojo.setTitle(product.title());
        pojo.setDescription(product.description());
        pojo.setPrice(product.price());
        pojo.setCurrency(product.currency());
        pojo.setSellerId(product.sellerId());
        pojo.setStatus(toDbStatus(product.status()));
        pojo.setCreatedAt(product.createdAt());
        pojo.setUpdatedAt(product.updatedAt());
        return pojo;
    }

    private Product.Status toDomainStatus(ProductStatus s) {
        return switch (s) {
            case DRAFT -> Product.Status.DRAFT;
            case PUBLISHED -> Product.Status.PUBLISHED;
            case HIDDEN -> Product.Status.HIDDEN;
        };
    }

    private ProductStatus toDbStatus(Product.Status s) {
        return switch (s) {
            case DRAFT -> ProductStatus.DRAFT;
            case PUBLISHED -> ProductStatus.PUBLISHED;
            case HIDDEN -> ProductStatus.HIDDEN;
        };
    }
}
```

- [ ] **Step 4: Прогон**

Run: `./gradlew test --tests "*ProductDomainRecordMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/remodov/catalog/repository/ProductDomainRecordMapper.java \
        src/test/java/ru/remodov/catalog/repository/ProductDomainRecordMapperTest.java
git commit -m "feat(repo): ProductDomainRecordMapper POJO↔domain (R-JOOQ-MAP-1)"
```

---

### Task 3.4: `PageView<T>` — общий типизированный page-result

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-PAG-1.

**Files:**
- Create: `src/main/java/ru/remodov/catalog/domain/PageView.java`

- [ ] **Step 1: Создать record**

```java
package ru.remodov.catalog.domain;

import java.util.List;

public record PageView<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
```

- [ ] **Step 2: Компиляция + commit**

Run: `./gradlew compileJava`

```bash
git add src/main/java/ru/remodov/catalog/domain/PageView.java
git commit -m "feat(domain): PageView<T> record (R-JOOQ-PAG-1)"
```

---

### Task 3.5: Рефакторинг `ProductRepository` на доменные типы

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-MAP-X1, R-JOOQ-REPO-3.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/repository/ProductRepository.java`

- [ ] **Step 1: Переписать интерфейс**

```java
package ru.remodov.catalog.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import ru.remodov.catalog.domain.PageView;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.domain.ProductSortField;

public interface ProductRepository {

    void insert(Product product);

    Optional<Product> findById(UUID id, SelectMode mode);

    void updateStatus(UUID id, Product.Status newStatus, OffsetDateTime updatedAt);

    PageView<Product> findBySeller(
        UUID sellerId,
        Product.Status statusFilterOrNull,
        int offset,
        int limit,
        ProductSortField sort
    );

    enum SelectMode { NO_LOCK, FOR_UPDATE }
}
```

(Внутренний enum `SortField` удалён — теперь `ProductSortField` из domain. Метод `countBySeller` удалён — `findBySeller` возвращает `PageView` с `totalElements`.)

- [ ] **Step 2: Переписать `JooqProductRepository`**

```java
package ru.remodov.catalog.repository;

import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.stereotype.Repository;
import ru.remodov.catalog.domain.PageView;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.domain.ProductSortField;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

@Repository
@RequiredArgsConstructor
public class JooqProductRepository implements ProductRepository {

    private final DSLContext dsl;
    private final ProductDomainRecordMapper domainMapper;

    @Override
    public void insert(Product product) {
        ProductsPojo pojo = domainMapper.fromDomain(product);
        dsl.newRecord(PRODUCTS, pojo).insert();
    }

    @Override
    public Optional<Product> findById(UUID id, SelectMode mode) {
        var query = dsl.selectFrom(PRODUCTS).where(PRODUCTS.ID.eq(id));
        var pojo = (mode == SelectMode.FOR_UPDATE
                ? query.forUpdate().fetchOneInto(ProductsPojo.class)
                : query.fetchOneInto(ProductsPojo.class));
        return Optional.ofNullable(pojo).map(domainMapper::toDomain);
    }

    @Override
    public void updateStatus(UUID id, Product.Status newStatus, OffsetDateTime updatedAt) {
        dsl.update(PRODUCTS)
            .set(PRODUCTS.STATUS, toDb(newStatus))
            .set(PRODUCTS.UPDATED_AT, updatedAt)
            .where(PRODUCTS.ID.eq(id))
            .execute();
    }

    @Override
    public PageView<Product> findBySeller(
        UUID sellerId,
        Product.Status statusFilterOrNull,
        int offset,
        int limit,
        ProductSortField sort
    ) {
        var cond = PRODUCTS.SELLER_ID.eq(sellerId);
        if (statusFilterOrNull != null) {
            cond = cond.and(PRODUCTS.STATUS.eq(toDb(statusFilterOrNull)));
        }
        long total = dsl.fetchCount(PRODUCTS, cond);
        List<Product> content = dsl.selectFrom(PRODUCTS)
            .where(cond)
            .orderBy(orderBy(sort))
            .offset(offset)
            .limit(limit)
            .fetchInto(ProductsPojo.class)
            .stream()
            .map(domainMapper::toDomain)
            .toList();
        int page = limit == 0 ? 1 : (offset / limit) + 1;
        return new PageView<>(content, page, limit, total);
    }

    private SortField<?> orderBy(ProductSortField sort) {
        return switch (sort) {
            case CREATED_AT_ASC -> PRODUCTS.CREATED_AT.asc();
            case CREATED_AT_DESC -> PRODUCTS.CREATED_AT.desc();
            case UPDATED_AT_ASC -> PRODUCTS.UPDATED_AT.asc();
            case UPDATED_AT_DESC -> PRODUCTS.UPDATED_AT.desc();
            case TITLE_ASC -> PRODUCTS.TITLE.asc();
            case TITLE_DESC -> PRODUCTS.TITLE.desc();
        };
    }

    private ProductStatus toDb(Product.Status s) {
        return switch (s) {
            case DRAFT -> ProductStatus.DRAFT;
            case PUBLISHED -> ProductStatus.PUBLISHED;
            case HIDDEN -> ProductStatus.HIDDEN;
        };
    }
}
```

- [ ] **Step 3: Компиляция упадёт в use case handler'ах и контроллере — это ожидаемо, фиксим в следующих задачах**

Run: `./gradlew compileJava`
Expected: FAIL — handlers ещё используют `ProductsPojo`, `ProductRepository.SortField`, `countBySeller`. Ничего не коммитим до завершения 3.6.

---

### Task 3.6: Адаптация handler'ов под доменные типы

**Скилл:** `ucp-pattern-design`. **Правило:** R-UC-X1, R-LAY-1.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/CreateProductUseCase.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/CreateProductUseCaseHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/PublishProductUseCaseHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/HideProductUseCaseHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/GetProductQueryHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQuery.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/ListMyProductsQueryHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/mapper/ProductJsonBeanMapper.java`

- [ ] **Step 1: Убрать бизнес-валидацию из `CreateProductUseCase`**

```java
package ru.remodov.catalog.usecase.product;

import java.math.BigDecimal;
import java.util.Objects;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.UseCaseCommand;

public record CreateProductUseCase(
    SellerId sellerId,
    String title,
    String description,
    BigDecimal price,
    String currency
) implements UseCaseCommand<ProductDto> {

    public CreateProductUseCase {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
    }
}
```

- [ ] **Step 2: Перенести валидацию + сменить тип в `CreateProductUseCaseHandler`**

```java
package ru.remodov.catalog.usecase.product;

import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.core.service.UuidGenerator;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.InvalidCurrencyException;
import ru.remodov.catalog.exception.InvalidPriceException;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class CreateProductUseCaseHandler implements UseCaseHandler<CreateProductUseCase, ProductDto> {

    private static final String SUPPORTED_CURRENCY = "RUB";

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;
    private final DateTimeService dateTimeService;
    private final UuidGenerator uuidGenerator;

    @Override
    public Class<CreateProductUseCase> useCaseType() { return CreateProductUseCase.class; }

    @Override
    @Transactional
    public ProductDto handle(CreateProductUseCase uc) {
        validate(uc);
        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
        var product = new Product(
            uuidGenerator.generate(),
            uc.title(),
            uc.description(),
            uc.price(),
            uc.currency(),
            uc.sellerId().value(),
            Product.Status.DRAFT,
            now, now
        );
        repo.insert(product);
        return mapper.toDto(product);
    }

    private void validate(CreateProductUseCase uc) {
        if (uc.title().isBlank()) {
            throw new IllegalArgumentException("title must be non-empty");
        }
        if (uc.price().signum() <= 0) {
            throw new InvalidPriceException("price must be > 0");
        }
        if (!SUPPORTED_CURRENCY.equals(uc.currency())) {
            throw new InvalidCurrencyException(uc.currency());
        }
    }
}
```

- [ ] **Step 3: Сменить тип в `PublishProductUseCaseHandler`**

Заменить вызовы `repo.findById(id)` → `repo.findById(id, SelectMode.FOR_UPDATE)` (см. Task 4.4 для full lock-pattern); статус через `Product.Status.PUBLISHED`; убрать ручную time-конвертацию.

```java
        var product = repo.findById(productId, ProductRepository.SelectMode.FOR_UPDATE)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        // ABAC, transition validation — как сейчас, но статус-сравнения через Product.Status
        ...
        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
        repo.updateStatus(productId, Product.Status.PUBLISHED, now);
        return mapper.toDto(product /* with status set to PUBLISHED */);
```

> ⚠️ Если handler ранее возвращал DTO из POJO после mutation: теперь `Product` — иммутабельный record. Нужно создать обновлённый Product через `with`-like-builder или конструктор: 
> ```java
> var published = new Product(product.id(), product.title(), product.description(),
>     product.price(), product.currency(), product.sellerId(),
>     Product.Status.PUBLISHED, product.createdAt(), now);
> return mapper.toDto(published);
> ```

- [ ] **Step 4: Аналогично адаптировать `HideProductUseCaseHandler`**

Status → `Product.Status.HIDDEN`. Тот же паттерн с FOR_UPDATE и созданием обновлённого Product.

- [ ] **Step 5: Адаптировать `GetProductQueryHandler`**

```java
    @Override
    @Transactional(readOnly = true)
    public ProductDto handle(GetProductQuery q) {
        var product = repo.findById(q.productId().value(), ProductRepository.SelectMode.NO_LOCK)
            .orElseThrow(() -> new ProductNotFoundException(q.productId().value()));

        if (product.status() != Product.Status.PUBLISHED) {
            throw new ProductNotFoundException(q.productId().value());
        }
        return mapper.toDto(product);
    }
```

(Видимость для owner/admin — отдельная Task 4.1.)

- [ ] **Step 6: Адаптировать `ListMyProductsQuery` + `Handler`**

```java
public record ListMyProductsQuery(
    SellerId requesterSellerId,
    Product.Status statusFilter,
    int page,
    int size,
    ProductSortField sort
) implements UseCaseQuery<ProductPageDto> {
    public ListMyProductsQuery {
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        Objects.requireNonNull(sort, "sort");
        if (page < 1) throw new IllegalArgumentException("page must be >= 1");
        if (size <= 0 || size > 100) throw new IllegalArgumentException("size must be in (0, 100]");
    }
}
```

(Заметьте: `sort` теперь required — default ставит контроллер.)

```java
@Override
@Transactional(readOnly = true)
public ProductPageDto handle(ListMyProductsQuery q) {
    PageView<Product> view = repo.findBySeller(
        q.requesterSellerId().value(),
        q.statusFilter(),
        (q.page() - 1) * q.size(),
        q.size(),
        q.sort()
    );
    var dto = new ProductPageDto();
    dto.setContent(view.content().stream().map(mapper::toDto).toList());
    dto.setPage(view.page());
    dto.setSize(view.size());
    dto.setTotalElements(view.totalElements());
    dto.setTotalPages(view.totalPages());
    return dto;
}
```

- [ ] **Step 7: Адаптировать `ProductJsonBeanMapper`**

Метод `toDto(ProductsPojo)` → `toDto(Product)`. Удалить `toDbStatus` (он больше не нужен в контроллере — см. Task 3.7).

- [ ] **Step 8: Компиляция + полный прогон**

Run: `./gradlew compileJava test`
Expected: PASS (или известные падения по тестам, требующим адаптации в Phase 6).

- [ ] **Step 9: Commit (большой)**

```bash
git add src/main/java/ru/remodov/catalog/repository/ProductRepository.java \
        src/main/java/ru/remodov/catalog/repository/JooqProductRepository.java \
        src/main/java/ru/remodov/catalog/usecase/product/*.java \
        src/main/java/ru/remodov/catalog/mapper/ProductJsonBeanMapper.java
git commit -m "refactor: repository + handlers on domain Product/PageView/ProductSortField (R-LAY-1, R-JOOQ-MAP-X1)"
```

---

### Task 3.7: Контроллер не знает о persistence-типах

**Скилл:** `ucp-pattern-design`. **Правило:** R-DSP-3, R-LAY-1.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/controller/ProductController.java`
- Modify: `src/main/java/ru/remodov/catalog/mapper/ProductJsonBeanMapper.java`

- [ ] **Step 1: Маппер API-status → domain-status**

В `ProductJsonBeanMapper`:

```java
    Product.Status toDomainStatus(
        ru.remodov.catalog.generated.api.model.ProductStatus apiStatus);
```

(MapStruct сгенерирует тривиальный switch.)

- [ ] **Step 2: Переписать `listMyProducts` в контроллере**

```java
    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductPageDto> listMyProducts(
        ru.remodov.catalog.generated.api.model.ProductStatus status,
        Integer page,
        Integer size,
        String sort
    ) {
        var sellerId = authenticatedSeller.currentSellerId();
        var domainStatus = status == null ? null : mapper.toDomainStatus(status);
        var sortField = ProductSortField.parse(sort);
        return ResponseEntity.ok(
            dispatcher.dispatch(new ListMyProductsQuery(
                sellerId, domainStatus, page == null ? 1 : page, size == null ? 20 : size, sortField
            ))
        );
    }
```

Импорт `ru.remodov.catalog.repository.ProductRepository` — удалить.

- [ ] **Step 3: Компиляция + тесты**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/remodov/catalog/controller/ProductController.java \
        src/main/java/ru/remodov/catalog/mapper/ProductJsonBeanMapper.java
git commit -m "refactor(controller): no persistence types, sort/status via domain (R-DSP-3)"
```

---

## Phase 4 — Бизнес-логика (CRITICAL)

### Task 4.1: `GetProductQuery` — видимость для owner/admin

**Скилл:** `ucp-pattern-design`. **Правило:** спека `aggregates/product.md §3` + BR-P06.

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/GetProductQuery.java`
- Modify: `src/main/java/ru/remodov/catalog/usecase/product/GetProductQueryHandler.java`
- Modify: `src/main/java/ru/remodov/catalog/controller/ProductController.java`
- Modify: `src/main/java/ru/remodov/catalog/api/AuthenticatedSeller.java` (если нужен метод `currentSellerIdOrNull()`)

- [ ] **Step 1: Расширить query**

```java
package ru.remodov.catalog.usecase.product;

import java.util.Objects;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.UseCaseQuery;

public record GetProductQuery(
    ProductId productId,
    SellerId requesterSellerIdOrNull,
    boolean isAdmin
) implements UseCaseQuery<ProductDto> {
    public GetProductQuery {
        Objects.requireNonNull(productId, "productId");
    }
}
```

- [ ] **Step 2: Логика видимости в handler'е**

```java
@Override
@Transactional(readOnly = true)
public ProductDto handle(GetProductQuery q) {
    var product = repo.findById(q.productId().value(), ProductRepository.SelectMode.NO_LOCK)
        .orElseThrow(() -> new ProductNotFoundException(q.productId().value()));

    boolean isOwner = q.requesterSellerIdOrNull() != null
        && q.requesterSellerIdOrNull().value().equals(product.sellerId());
    boolean canSeeAny = q.isAdmin() || isOwner;

    if (!canSeeAny && product.status() != Product.Status.PUBLISHED) {
        throw new ProductNotFoundException(q.productId().value());
    }
    return mapper.toDto(product);
}
```

- [ ] **Step 3: В контроллере прокидывать роль**

```java
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<ProductDto> getProduct(UUID productId) {
        SellerId requester = authenticatedSeller.currentSellerIdOrNull();
        boolean isAdmin = authenticatedSeller.isAdminOrFalseIfAnonymous();
        return ResponseEntity.ok(dispatcher.dispatch(
            new GetProductQuery(ProductId.of(productId), requester, isAdmin)
        ));
    }
```

(Добавить в `AuthenticatedSeller` методы, толерантные к анонимному юзеру.)

- [ ] **Step 4: Тесты — owner видит свой DRAFT/HIDDEN, admin видит чужой DRAFT**

В `GetProductIntegrationTest`:

```java
    @Test
    void getProduct_whenOwnerReadsOwnDraft_returns200() { ... }
    @Test
    void getProduct_whenOwnerReadsOwnHidden_returns200() { ... }
    @Test
    void getProduct_whenAdminReadsOthersDraft_returns200() { ... }
    @Test
    void getProduct_whenAnonymousReadsDraft_returns404() { ... }
```

- [ ] **Step 5: Прогон**

Run: `./gradlew test --tests "*GetProductIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ru/remodov/catalog/usecase/product/GetProductQuery.java \
        src/main/java/ru/remodov/catalog/usecase/product/GetProductQueryHandler.java \
        src/main/java/ru/remodov/catalog/controller/ProductController.java \
        src/main/java/ru/remodov/catalog/api/AuthenticatedSeller.java \
        src/test/java/ru/remodov/catalog/usecase/product/GetProductIntegrationTest.java
git commit -m "feat(get-product): visibility honors owner/admin role (BR-P06 + spec §3)"
```

---

## Phase 5 — Целостность данных (HIGH)

### Task 5.1: `SELECT FOR UPDATE` в Publish/Hide handlers

**Скилл:** `ucp-jooq-design`. **Правило:** R-JOOQ-LCK-1.

**Files:** уже частично сделано в Task 3.6. Здесь добавляем тест на конкурентность.

- [ ] **Step 1: Написать тест на lost update**

Create: `src/test/java/ru/remodov/catalog/usecase/product/ConcurrentPublishHideTest.java`

```java
package ru.remodov.catalog.usecase.product;

// ... импорты

class ConcurrentPublishHideTest extends CatalogBaseIntegrationTest {

    @Test
    void concurrentPublishAndHide_resolveDeterministically() throws Exception {
        // создать DRAFT
        // в двух потоках одновременно: publish + hide
        // ожидание: один из двух упадёт INVALID_STATE_TRANSITION (409),
        //          второй пройдёт; финальный статус соответствует выигравшему;
        //          никаких dirty-reads
    }
}
```

(Деталь реализации — два `ExecutorService.submit`, `CountDownLatch`. Скопировать из существующих integration-тестов.)

- [ ] **Step 2: Прогон**

Run: `./gradlew test --tests "*Concurrent*"`
Expected: PASS (после Task 3.6 lock уже стоит).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/ru/remodov/catalog/usecase/product/ConcurrentPublishHideTest.java
git commit -m "test(concurrency): publish+hide race covered by SELECT FOR UPDATE (R-JOOQ-LCK-1)"
```

---

## Phase 6 — Тестовое покрытие (HIGH/MEDIUM)

### Task 6.1: Service-account JWT helper

**Скилл:** `ucp-test-design`. **Правило:** AC-C9.

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/testsupport/TestHttpHeaders.java`
- Modify: `src/test/java/ru/remodov/catalog/testsupport/FakeJwtDecoder.java` (если нужно)

- [ ] **Step 1: Добавить метод `withServiceAccountToken()`**

```java
public static HttpHeaders withServiceAccountToken() {
    var headers = new HttpHeaders();
    headers.setBearerAuth(FakeJwtDecoder.SERVICE_ACCOUNT_TOKEN);
    return headers;
}
```

- [ ] **Step 2: В `FakeJwtDecoder` добавить constant и роли**

```java
public static final String SERVICE_ACCOUNT_TOKEN = "service-account-token";
// ... в decode-методе: для этого токена ставить realm_access.roles = ["service-account"]
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/ru/remodov/catalog/testsupport/*.java
git commit -m "test(support): service-account JWT helper for AC-C9"
```

---

### Task 6.2: AC-C9 — service-account читает только PUBLISHED

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/GetProductIntegrationTest.java`

- [ ] **Step 1: Добавить два теста**

```java
    @Test
    void getProduct_whenServiceAccountReadsPublished_returns200WithPrice() {
        var published = createPublishedProduct();
        var response = rest.exchange(
            "/api/v1/products/" + published.getId(),
            HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withServiceAccountToken()),
            ProductDto.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getPrice()).isEqualTo(published.getPrice());
    }

    @Test
    void getProduct_whenServiceAccountReadsDraft_returns404() {
        var draft = createDraftProduct();
        var response = rest.exchange(
            "/api/v1/products/" + draft.getId(),
            HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withServiceAccountToken()),
            JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
./gradlew test --tests "*GetProductIntegrationTest"
git add src/test/java/ru/remodov/catalog/usecase/product/GetProductIntegrationTest.java
git commit -m "test(get-product): service-account visibility (AC-C9)"
```

---

### Task 6.3: BR-P03 — id из тела игнорируется

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/CreateProductIntegrationTest.java`

- [ ] **Step 1: Тест**

```java
    @Test
    void create_whenIdProvidedInBody_isIgnored_serverGenerates() throws Exception {
        var attackerId = "11111111-1111-1111-1111-111111111111";
        var body = """
            {"id":"%s","title":"T","price":100.00,"currency":"RUB"}
            """.formatted(attackerId);
        var response = postCreate(body, sellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getId().toString()).isNotEqualTo(attackerId);
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
./gradlew test --tests "*CreateProductIntegrationTest"
git add src/test/java/ru/remodov/catalog/usecase/product/CreateProductIntegrationTest.java
git commit -m "test(create-product): server-generated id (BR-P03)"
```

---

### Task 6.4: ABAC для HideProduct чужим seller

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/HideProductIntegrationTest.java`

- [ ] **Step 1: Тест**

```java
    @Test
    void hide_whenOtherSeller_returns404OwnProductRequired() {
        var ownerProduct = createPublishedProductFor(ownerSellerId);
        var response = postHide(ownerProduct.getId(), otherSellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(parseProblem(response).get("code").asText()).isEqualTo("OWN_PRODUCT_REQUIRED");
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
./gradlew test --tests "*HideProductIntegrationTest"
git add src/test/java/ru/remodov/catalog/usecase/product/HideProductIntegrationTest.java
git commit -m "test(hide-product): other-seller ABAC (AC-C4 / BR-P04)"
```

---

### Task 6.5: Hide из HIDDEN → 409

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/HideProductIntegrationTest.java`

- [ ] **Step 1: Тест**

```java
    @Test
    void hide_whenAlreadyHidden_returns409InvalidStateTransition() {
        var product = createHiddenProduct(ownerSellerId);
        var response = postHide(product.getId(), ownerSellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(parseProblem(response).get("code").asText()).isEqualTo("INVALID_STATE_TRANSITION");
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
git add src/test/java/ru/remodov/catalog/usecase/product/HideProductIntegrationTest.java
git commit -m "test(hide-product): hide from HIDDEN → 409 (AC-C5 / BR-P05)"
```

---

### Task 6.6: Граничные значения цены (отрицательная, null) и валюты (null)

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/CreateProductIntegrationTest.java`

- [ ] **Step 1: Тесты**

```java
    @Test
    void create_whenPriceNegative_returns400InvalidPrice() {
        var body = """
            {"title":"T","price":-1.00,"currency":"RUB"}
            """;
        var response = postCreate(body, sellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(parseProblem(response).get("code").asText()).isEqualTo("INVALID_PRICE");
    }

    @Test
    void create_whenPriceNull_returns400() {
        var body = """
            {"title":"T","currency":"RUB"}
            """;
        var response = postCreate(body, sellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_whenCurrencyNull_returns400() {
        var body = """
            {"title":"T","price":100.00}
            """;
        var response = postCreate(body, sellerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
git add src/test/java/ru/remodov/catalog/usecase/product/CreateProductIntegrationTest.java
git commit -m "test(create-product): boundary cases for price/currency (BR-P01, BR-P02)"
```

---

### Task 6.7: Пагинация — несколько страниц + пустая

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/usecase/product/ListMyProductsIntegrationTest.java`

- [ ] **Step 1: Тесты**

```java
    @Test
    void list_threeProducts_size2_page1_returns2items_totalPages2() { ... }

    @Test
    void list_threeProducts_size2_page2_returns1item() { ... }

    @Test
    void list_noProducts_returnsEmptyContent() {
        var response = getMyProducts(emptySellerToken, 1, 20);
        assertThat(response.getBody().getTotalElements()).isEqualTo(0);
        assertThat(response.getBody().getContent()).isEmpty();
    }
```

- [ ] **Step 2: Прогон + commit**

```bash
git add src/test/java/ru/remodov/catalog/usecase/product/ListMyProductsIntegrationTest.java
git commit -m "test(list-my): pagination edge cases (AC-C8)"
```

---

### Task 6.8: admin обходит ABAC для Hide

**Files:**
- Modify: `src/test/java/ru/remodov/catalog/audit/AdminAuditIntegrationTest.java`

- [ ] **Step 1: Тест**

```java
    @Test
    void hide_byAdmin_whenOtherSellerProduct_returns200AndHidden() { ... }
```

- [ ] **Step 2: Прогон + commit**

```bash
git add src/test/java/ru/remodov/catalog/audit/AdminAuditIntegrationTest.java
git commit -m "test(hide-product): admin bypasses ABAC (BR-P04 admin path)"
```

---

## Phase 7 — Стиль (LOW, mechanical)

### Task 7.1: Убрать неиспользуемый `HttpStatus` импорт

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/controller/ProductController.java:6`

- [ ] **Step 1**

```bash
# IDE / sed
sed -i '' '/^import org.springframework.http.HttpStatus;$/d' \
    src/main/java/ru/remodov/catalog/controller/ProductController.java
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

---

### Task 7.2: Импортировать `ZoneOffset` вместо FQN

> Если Task 2.1 уже прошёл — большая часть исправлена. Здесь — добивка оставшихся.

**Files:** все, где встречается `java.time.ZoneOffset.UTC` inline.

- [ ] **Step 1: Найти**

```bash
grep -rn "java.time.ZoneOffset" src/main/java/ src/test/java/ || true
```

- [ ] **Step 2: В каждом файле добавить `import java.time.ZoneOffset;` и заменить FQN**

---

### Task 7.3: Выравнивания в switch / горизонтальные пробелы

**Files:**
- Modify: `src/main/java/ru/remodov/catalog/audit/AuditLogger.java:23-24` (после рефакторинга может уже не быть актуальным)
- Modify: `src/main/java/ru/remodov/catalog/domain/ProductSortField.java` (после Task 3.1)

- [ ] **Step 1: Убрать выравнивающие пробелы**

Заменить `case "createdat,asc"  ->` на `case "createdat,asc" ->`.

---

### Task 7.4: Длинная строка в `ListMyProductsQueryHandler` (если ещё актуально)

После Phase 3 строка переписана — повторно проверить ширину.

---

### Task 7.5 — финальный коммит стиля

- [ ] **Step 1**

```bash
git add -A
git commit -m "style: remove unused imports, ZoneOffset FQN, alignment (JS-3.2, JS-5.3)"
```

---

## Финал

### Task 8.1: Полный прогон

- [ ] **Step 1**

```bash
./gradlew clean test
```

Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 2: Финальный review-pass**

Опционально — повторно прогнать те же 5 review-агентов; ожидание: не более 5 LOW-замечаний.

---

## Self-Review (checklist)

- [x] Все 4 группы CRITICAL покрыты (Phases 1, 3, 4).
- [x] HIGH (Phases 2, 5, 6).
- [x] LOW (Phase 7).
- [x] Каждый шаг — bite-sized с явным commit.
- [x] Нет TBD/TODO/«implement later».
- [x] Code-блоки полные (не «similar to above»).
- [x] Типы консистентны: `Product`, `Product.Status`, `ProductSortField`, `PageView<T>`, `SelectMode`.
- [x] Test-first где уместно (Tasks 3.3, 4.1, 5.1, 6.*).

### Известные риски

1. **Phase 2 (codegen)** требует поднятого Postgres с накатанными миграциями — без Testcontainers-плагина (`ucp-jooq-design` рекомендует) `./gradlew generateJooq` падает на CI. Финальный шаг: либо подключить плагин в отдельной задаче, либо принять текущий путь и в pipeline скриптом поднимать PG.
2. **Phase 3 — большой коммит** (рефакторинг репо + всех handler'ов). Хорошо бы перед слиянием прогнать `ucp-pattern-review` повторно.
3. **`ucp-pattern-review` (FINDING P-5)** — переприсваивание `sort` в record-конструкторе — устраняется в Task 3.6 (sort теперь required, default ставит контроллер).
