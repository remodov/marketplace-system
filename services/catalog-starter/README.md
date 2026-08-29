# Каталог: учебная версия

Тот же каталог маркетплейса, что и в `services/catalog`, только написанный так,
как пишут в обычном проекте: контроллер → сервис → репозиторий, Spring Data JPA,
одна таблица, никакой кодогенерации. С этого начинается практикум.

Взрослая версия рядом — она умеет больше, но и требует больше: гексагональные
модули, jOOQ с генерацией классов из живой базы, спецификация Bounded Context.
Сравнить их полезно самому: одно и то же поведение, две разные цены владения.

## Запустить

```bash
docker compose -f ../../infra/compose.yaml up -d postgres-catalog
../../gradlew bootRun
```

Приложение поднимется на 8082, схему накатит Liquibase при старте.

```bash
curl -s localhost:8082/products
curl -s -X POST localhost:8082/products -H 'Content-Type: application/json' \
  -d '{"title":"Беспроводная мышь","price":1990.00,"stock":7}'
curl -s -X POST localhost:8082/products/<id>/reserve -H 'Content-Type: application/json' \
  -d '{"quantity":2}'
curl -s -X PATCH localhost:8082/products/<id>/price -H 'Content-Type: application/json' \
  -d '{"price":1490.00}'
curl -s -X PATCH localhost:8082/products/<id>/discount -H 'Content-Type: application/json' \
  -d '{"percent":20}'
curl -s -X PATCH localhost:8082/products/<id>/stock -H 'Content-Type: application/json' \
  -d '{"delta":7}'
curl -s 'localhost:8082/products?maxPrice=2000'
```

Резерв не списывает товар со склада: `stock` остаётся прежним, растёт `reserved`,
а продать можно `available = stock - reserved`. Карточка товара кэшируется в Redis
и сбрасывается при каждом изменении — стенд поднимает Redis рядом с базой.

## Прогнать тесты

```bash
../../gradlew test
```

Тесты идут на H2 в памяти и на кэше в памяти — Docker для них не нужен, поэтому
проверить себя можно сразу, ещё до знакомства с контейнерами. Скорость на H2 не
меряется: числа снимаются на настоящей базе, см. `bench/README.md`.

## Что внутри

| файл | зачем |
|---|---|
| `Product` | сущность и правила: резерв не больше доступного, цена положительная, скидка не глубже половины |
| `ProductRepository` | Spring Data JPA, метод поиска выводится из имени |
| `ProductService` | границы транзакций, сценарии: найти, создать, зарезервировать |
| `ProductController` | REST: чтение каталога и карточки, создание, резерв, смена цены, скидка, приход и списание |
| `ProductCard` | то, что уходит наружу и лежит в кэше — отдельно от сущности |
| `ProductExceptionHandler` | доменные ошибки → коды 404 и 409, тело в формате Problem Details |

Правило, вокруг которого всё крутится, живёт в сущности, а не в сервисе:

```java
public void reserve(int quantity) {
    if (quantity > stock) {
        throw new OutOfStockException(id, quantity, stock);
    }
    stock -= quantity;
}
```

Это первый шаг к тому, что дальше в программе называется доменной моделью: правило
нельзя обойти, потому что менять остаток снаружи нечем.
