# Маркетплейс: сквозная система для практики

Рабочая система из разбора [«Как разбить систему на сервисы»](https://vikulin-va.ru/case/services-map/):
шесть сервисов с собственными базами, событиями через Kafka и сагой на оформлении
заказа. Её можно поднять, потрогать и читать как образец — и на ней же построен
[практикум из пятнадцати шагов](docs/practicum/PLAN.md).

Репозиторий — практическая часть программы
[«Backend · Java»](https://vikulin-va.ru/programs/backend-java/) с
[vikulin-va.ru](https://vikulin-va.ru/): каждый шаг практикума привязан к фазе
программы и к статьям, которые эту фазу закрывают. Полная карта специализаций —
[«Программы обучения»](https://vikulin-va.ru/curriculum/), формат занятий —
[«Курсы»](https://vikulin-va.ru/courses/).

## Что внутри

| сервис | отвечает за | не делает | разбор |
|---|---|---|---|
| `services/catalog` | карточки, остатки, поиск, модерация | не считает деньги | [Catalog Service](https://vikulin-va.ru/case/catalog-service/) |
| `services/order` | корзина, заказ, резерв, статусы | не пишет в каталог | [Order Service](https://vikulin-va.ru/case/order-service/) |
| `services/payment` | платежи, возвраты, автомат статусов (голый JDBC) | не знает про заказы | [Сага](https://vikulin-va.ru/distributed-patterns-style-guide/saga/) |
| `services/notification` | email, sms, push по команде | ничего не решает само | [Notification Service](https://vikulin-va.ru/case/notification-service/) |
| `services/customer` | identity, профили, авторизация | не хранит товары | [Keycloak и токены](https://vikulin-va.ru/keycloak/) |
| `services/backoffice` | модерация, споры, аудит | не правит чужие данные напрямую | [Карта сервисов](https://vikulin-va.ru/case/services-map/) |
| `services/catalog-starter` | тот же каталог, но написанный просто | не прячет ничего за абстракциями | [Разбор каталога по шагам](https://vikulin-va.ru/case/catalog-service-walkthrough/) |
| `services/bff` | сборка экрана и лимит частоты на границе | не содержит бизнес-правил | [Стили API](https://vikulin-va.ru/api-styles/) |
| `web` | витрина, корзина, оформление и воронка | не ходит в сервисы напрямую | [Frontend](https://vikulin-va.ru/frontend/) |

Границы взяты из кейса и держатся намеренно: ограничение «что сервис **не** делает»
здесь важнее списка его функций. Почему границы проведены именно так — в разборе
[«Как разбить систему на сервисы»](https://vikulin-va.ru/case/services-map/) и в
разделе [«Проектирование систем»](https://vikulin-va.ru/system-design/).

## С чего начинать

Если вы здесь впервые, взрослые сервисы читать рано. Начните с
[`services/catalog-starter`](services/catalog-starter/README.md) — тот же каталог,
написанный так, как пишут в обычном проекте: контроллер → сервис → репозиторий,
[Spring Data JPA](https://vikulin-va.ru/spring/data-jpa/), одна таблица, никакой
кодогенерации. Первые шесть шагов практикума идут по нему.

## Поднять стенд

```bash
docker compose -f infra/compose.yaml up -d
docker compose -f infra/compose.yaml ps
```

Поднимаются: по своей PostgreSQL на каждый сервис (общей базы у сервисов нет),
Kafka, Redis, MongoDB, Elasticsearch, MinIO и Keycloak. Что это за инструменты и
когда какой из них уместен — в разделах сайта, ссылки в таблице.

| что | порт | зачем | почитать |
|---|---|---|---|
| PostgreSQL order / customer / notification / backoffice / payment / catalog / catalog-starter | 5433–5439 | источник правды каждого сервиса | [PostgreSQL](https://vikulin-va.ru/postgres/) |
| Kafka | 9092 | события заказа, outbox, сага | [Kafka](https://vikulin-va.ru/kafka/) |
| Redis | 6379 | кэш карточек, счётчики лимита частоты | [Redis](https://vikulin-va.ru/redis/) |
| MongoDB | 27017 | журнал доставок и шаблоны уведомлений | [MongoDB](https://vikulin-va.ru/mongodb/) |
| Elasticsearch | 9200 | поиск по каталогу | [Elasticsearch](https://vikulin-va.ru/elasticsearch/) |
| MinIO | 9000, 9001 | изображения товаров, S3-совместимое хранилище | [Объектные хранилища](https://vikulin-va.ru/object-storage/) |
| Keycloak | 8081 | выдача токенов; сервисы проверяют подпись локально | [Keycloak](https://vikulin-va.ru/keycloak/) |

Сам стенд — обычный [Docker Compose](https://vikulin-va.ru/docker/): контейнеры
поднимаются одной командой и так же выбрасываются.

## Собрать и прогнать тесты

```bash
./gradlew buildAll     # собрать все сервисы
./gradlew testAll      # прогнать тесты всех сервисов
```

Каждый сервис остаётся самостоятельной сборкой — его можно открыть и запустить
отдельно, как если бы он жил своим репозиторием. Как устроены тесты и почему их
столько — в разделе [«Тестирование»](https://vikulin-va.ru/testing/).

## Практикум

Пятнадцать шагов и два боковых — от «поднять систему» до наблюдаемости и доставки,
с покрытием всех фаз программы
[«Backend · Java»](https://vikulin-va.ru/programs/backend-java/).
План целиком: [docs/practicum/PLAN.md](docs/practicum/PLAN.md).

На каждый шаг две ветки: `step-NN-<тема>` — задание (каркас на месте, реализация
вынута, тест красный) и `step-NN-<тема>-solution` — эталон. Условие шага лежит
в `TASK.md` внутри сервиса.

| шаг | о чём | ветка задания | материал |
|---|---|---|---|
| 1 | запустить и разобрать готовый сервис | — | [Spring Boot](https://vikulin-va.ru/spring/boot-auto-configuration/) · [Внедрение зависимостей](https://vikulin-va.ru/spring/di-and-lifecycle/) |
| 2 | ещё одна ручка чтения и фильтр по цене | `step-02-read-endpoint` | [Spring Data JPA](https://vikulin-va.ru/spring/data-jpa/) · [@Transactional](https://vikulin-va.ru/spring/transactional/) |
| 3 | команды, валидация, коды ошибок | `step-03-commands-and-errors` | [Где валидировать](https://vikulin-va.ru/validation/where-to-validate/) · [Модель ошибок](https://vikulin-va.ru/error-handling/error-model/) · [Ошибки в REST](https://vikulin-va.ru/rest-api/errors/) |
| 4 | правило внутри модели | `step-04-domain-rules` | [Что такое DDD](https://vikulin-va.ru/ddd/01-what-is-ddd/) · [Современная Java](https://vikulin-va.ru/java/records-and-modern/) |
| 5 | резерв, миграция, сто покупателей разом | `step-05-stock-and-locking` | [PostgreSQL](https://vikulin-va.ru/postgres/) · [Транзакции и блокировки](https://vikulin-va.ru/hibernate/transactions-and-locking/) · [Гонки](https://vikulin-va.ru/concurrency/race-conditions/) |
| 6 | поиск: индекс и кэш | `step-06-search-and-cache` | [Типы индексов](https://vikulin-va.ru/postgres/indexes-types/) · [Кэширование](https://vikulin-va.ru/redis/caching-patterns/) |
| 7 | тот же каталог, но по-взрослому: UseCase, Handler, порт | `step-07-usecase-and-handler` | [Use Case Pattern](https://vikulin-va.ru/use-case-pattern/) · [Гексагональная архитектура](https://vikulin-va.ru/hexagonal/) |
| 8 | сосед отвечает медленно, срывается и лежит | `step-08-resilience` | [Устойчивость](https://vikulin-va.ru/patterns/resilience/) · [Order Service](https://vikulin-va.ru/case/order-service/) |
| 9 | идемпотентность: один запрос — один заказ | `step-09-idempotency` | [Идемпотентность](https://vikulin-va.ru/distributed-patterns-style-guide/idempotency/) |
| 10 | событие через outbox и внешний контракт | `step-10-events-and-contract` | [Kafka](https://vikulin-va.ru/kafka/) · [Outbox и inbox](https://vikulin-va.ru/distributed-patterns-style-guide/outbox-inbox/) |
| 11 | сага и автомат статусов платежа | `step-11-saga-and-state-machine` | [Сага](https://vikulin-va.ru/distributed-patterns-style-guide/saga/) · [Автоматы](https://vikulin-va.ru/state-machines/what-is-a-state-machine/) |
| 12 | токены, роли и файлы мимо сервиса | `step-12-tokens-and-files` | [Keycloak](https://vikulin-va.ru/keycloak/) · [Объектные хранилища](https://vikulin-va.ru/object-storage/) |
| 13 | граница системы: экран и лимит частоты | `step-13-gateway-and-bff` | [Стили API](https://vikulin-va.ru/api-styles/) · [Redis](https://vikulin-va.ru/redis/) |
| 14 | веб-клиент и воронка покупки | `step-14-web-and-funnel` | [Frontend](https://vikulin-va.ru/frontend/) · [Продукт-инженер](https://vikulin-va.ru/product-engineer/) |
| 15 | образ, манифесты, пайплайн, наблюдаемость | `step-15-delivery-and-observability` | [Docker](https://vikulin-va.ru/docker/) · [Kubernetes](https://vikulin-va.ru/kubernetes/) · [Наблюдаемость](https://vikulin-va.ru/observability/) |
| Б1 | Notification на Kotlin | `side-b1-kotlin-notification` | [Kotlin](https://vikulin-va.ru/kotlin/) |
| Б2 | поиск по каталогу на естественном языке | `side-b2-llm-search` | [Продукт-инженер](https://vikulin-va.ru/product-engineer/) |

Шаги 1–6 идут по `services/catalog-starter`, дальше — по настоящим сервисам:
каталог, заказ, платежи, BFF, веб-клиент и выкат. Все шаги готовы; чем
проверяется каждый — написано в его `TASK.md`.

## Что почитать рядом

- [Разбор каталога по шагам](https://vikulin-va.ru/case/catalog-service-walkthrough/) — как этот сервис выглядит изнутри.
- [Тактический DDD](https://vikulin-va.ru/ddd/03-tactical-patterns/) — агрегаты, события, репозитории.
- [Архитектура и стили API](https://vikulin-va.ru/api-styles/) — почему здесь REST, а не GraphQL или gRPC.
- [Конечные автоматы](https://vikulin-va.ru/state-machines/) — статусы заказа как автомат, а не как строка.
- [Наблюдаемость](https://vikulin-va.ru/observability/) — логи, метрики и трассировка на этом же стенде.
