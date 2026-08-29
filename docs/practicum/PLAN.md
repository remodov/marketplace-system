# Практикум: маркетплейс за пятнадцать шагов

Сквозная практика к программе «Backend · Java». Ученик не пишет систему с нуля:
каркас, контракты, конфигурация и тесты даются. Он реализует то, ради чего шаг
и придуман, и проверяет себя не кнопкой «показать решение», а зелёным тестом.

Система — маркетплейс из разбора [«Как разбить систему на сервисы»](https://vikulin-va.ru/case/services-map/):
шесть сервисов (Catalog, Order, Payment, Notification, Customer, Backoffice),
API Gateway, BFF и веб-клиент.

Каждый шаг заканчивается системой, которая поднимается и работает. Сдаётся веткой
и pull request'ом — git тренируется по ходу, а не отдельным упражнением.

## Хранилища по шагам

Разные базы берутся не для коллекции, а там, где они уместны по задаче:

| хранилище | где | зачем именно оно |
|---|---|---|
| PostgreSQL | все сервисы | источник правды, транзакции, блокировки |
| Elasticsearch | Catalog | поиск с морфологией, опечатками и фасетами |
| Redis | Catalog, Gateway | кэш горячих карточек; счётчики лимита частоты |
| MongoDB | Notification | журнал доставок и шаблоны — документы неровной формы |
| Kafka | Order → все | события, outbox, сага |

---

## Шаг 1. Поднять систему и прочитать карту

**Материал:** [/case/services-map/](https://vikulin-va.ru/case/services-map/) · [/microservices/what-and-why/](https://vikulin-va.ru/microservices/what-and-why/) · [/docker/what-is-docker/](https://vikulin-va.ru/docker/what-is-docker/) · [/system-design/](https://vikulin-va.ru/system-design/)

**Фазы:** 15 микросервисы · 16 Docker · 23 системный дизайн

**Даётся:** общий `compose` (Postgres на сервис, Kafka, Redis, MongoDB,
Elasticsearch, Keycloak), корневой Gradle, README.

**Ученик:** поднимает систему, оформляет тестовый заказ через `curl`, находит по
логам путь запроса через сервисы, отвечает, кто чем владеет.

**Проверка:** контейнеры здоровы, сквозной сценарий отработал, ответы на вопросы
о владении данными сходятся с картой.

## Шаг 2. Спецификация и язык домена

**Материал:** [/use-case-pattern/](https://vikulin-va.ru/use-case-pattern/) · [/domain-driven-design/01-what-is-ddd/](https://vikulin-va.ru/domain-driven-design/01-what-is-ddd/) · [/case/order-service/](https://vikulin-va.ru/case/order-service/) · [/methodology/](https://vikulin-va.ru/methodology/)

**Фазы:** 24 DDD · 26 методологии · 29 UCP · 3 git · 21 работа с агентами

**Даётся:** спецификация Order Service (43 файла) как образец.

**Ученик:** по спецификации отвечает, что сервис делает и чего не делает, находит
в коде место каждого бизнес-правила; пишет недостающий раздел спеки Catalog —
язык, роли, правила. Здесь же настраивает свой процесс: ветка на шаг, pull
request, генерация каркаса скиллом по спецификации.

**Проверка:** ревью спеки по чек-листу; правило из спеки находится в коде.

## Шаг 3. Первый запрос: Query → Handler → Controller

**Материал:** [/spring/di-and-lifecycle/](https://vikulin-va.ru/spring/di-and-lifecycle/) · [/rest-api/](https://vikulin-va.ru/rest-api/) · [/patterns/cqrs/](https://vikulin-va.ru/patterns/cqrs/) · [/case/catalog-service/](https://vikulin-va.ru/case/catalog-service/)

**Фазы:** 4 ядро Spring · 12 REST · 29 UCP

**Даётся:** контракт OpenAPI, репозиторий, красный тест контроллера.

**Ученик:** пишет `GetProductQuery`, `GetProductHandler` и контроллер — первый use
case только на чтение.

**Проверка:** зелёный тест; ответ совпадает с контрактом.

## Шаг 4. Команда, валидация, модель ошибок

**Материал:** [/validation/where-to-validate/](https://vikulin-va.ru/validation/where-to-validate/) · [/error-handling/error-model/](https://vikulin-va.ru/error-handling/error-model/) · [/rest-api/java/errors/](https://vikulin-va.ru/rest-api/java/errors/)

**Фазы:** 12 контракты · 6 принципы и паттерны

**Даётся:** DTO, обработчик ошибок, тесты на коды ответа.

**Ученик:** команда создания карточки товара — проверки входа, доменные ошибки,
коды 400/409/422, единый формат тела ошибки.

**Проверка:** тесты на каждый сценарий отказа зелёные.

## Шаг 5. Домен: агрегат, value object, инварианты

**Материал:** [/domain-driven-design/](https://vikulin-va.ru/domain-driven-design/) · [/ddd/](https://vikulin-va.ru/ddd/) · [/java/records-and-modern/](https://vikulin-va.ru/java/records-and-modern/)

**Фазы:** 24 DDD · 1 основы языка (records, sealed)

**Даётся:** каркас пакета `domain` и тесты без Spring.

**Ученик:** `Product` как агрегат, `Money`, `Sku` как value object, инварианты
внутри домена, а не в сервисном слое.

**Проверка:** доменные тесты зелёные и не поднимают контекст Spring.

## Шаг 6. Схема, миграции, транзакции, блокировки

**Материал:** [/postgres/](https://vikulin-va.ru/postgres/) · [/hibernate/transactions-and-locking/](https://vikulin-va.ru/hibernate/transactions-and-locking/) · [/hibernate/persistence-context/](https://vikulin-va.ru/hibernate/persistence-context/)

**Фазы:** 7 PostgreSQL · 9 Hibernate и ORM · 8 эксплуатация

**Даётся:** Liquibase-скелет, тестовый контейнер Postgres.

**Ученик:** схема каталога, миграция, репозиторий, транзакционные границы,
оптимистичная блокировка при одновременном изменении остатка.

**Проверка:** тест на конкурентное обновление ловит конфликт версий.

## Шаг 7. Поиск: Elasticsearch и кэш в Redis

**Материал:** [/postgres/indexes-types/](https://vikulin-va.ru/postgres/indexes-types/) · [/redis/caching-patterns/](https://vikulin-va.ru/redis/caching-patterns/) · [/algorithms/](https://vikulin-va.ru/algorithms/) · [/architecture-choice/](https://vikulin-va.ru/architecture-choice/)

**Фазы:** 2 алгоритмы и структуры · 10 другие хранилища · 8 индексы

**Даётся:** поднятый Elasticsearch, индекс-маппинг, Redis, тесты выдачи.

**Ученик:** индексация карточек, поиск с морфологией и фасетами, кэш горячих
карточек в Redis и его инвалидация при изменении товара; сравнение с `LIKE` по
Postgres на объёме.

**Проверка:** поиск находит по опечатке, повторный запрос отвечает из кэша,
изменение товара кэш сбрасывает.

## Шаг 8. Резерв в Catalog: таймауты, ретраи, отказ соседа

**Материал:** [/patterns/resilience/](https://vikulin-va.ru/patterns/resilience/) · [/microservices/](https://vikulin-va.ru/microservices/) · [/rest-api/](https://vikulin-va.ru/rest-api/)

**Фазы:** 11 сетевой фундамент · 15 микросервисы · 25 паттерны сервиса

**Даётся:** клиент Catalog, стенд с управляемой задержкой и отказом.

**Ученик:** синхронный резерв остатка при оформлении, таймаут, повтор с
отступом, поведение при недоступном Catalog по плану отказов из кейса.

**Проверка:** тест с «упавшим» Catalog — заказ не создаётся, ошибка понятная.

## Шаг 9. Идемпотентность и гонки

**Материал:** [/concurrency/race-conditions/](https://vikulin-va.ru/concurrency/race-conditions/) · [/hibernate/transactions-and-locking/](https://vikulin-va.ru/hibernate/transactions-and-locking/) · [/patterns/idempotency/](https://vikulin-va.ru/patterns/idempotency/)

**Фазы:** 5 многопоточность · 8 блокировки · 12 контракты

**Даётся:** тест, который шлёт запрос дважды, и нагрузочный тест на один товар.

**Ученик:** `Idempotency-Key`, хранение результата операции, защита остатка от
двойного списания при параллельных заказах.

**Проверка:** повтор не создаёт второй заказ; сто параллельных заказов на десять
единиц товара продают ровно десять.

## Шаг 10. События, outbox и журнал доставки

**Материал:** [/kafka/](https://vikulin-va.ru/kafka/) · [/patterns/outbox/](https://vikulin-va.ru/patterns/outbox/) · [/architecture-choice/](https://vikulin-va.ru/architecture-choice/)

**Фазы:** 14 брокеры · 25 outbox · 10 другие хранилища (MongoDB)

**Даётся:** таблица outbox, консьюмер-каркас в Notification, MongoDB.

**Ученик:** публикация `OrderCreated` через outbox, потребление в Notification,
журнал доставок и шаблоны в MongoDB, защита от повторной отправки.

**Проверка:** падение сервиса между записью и отправкой не теряет событие;
повторная доставка не шлёт второе письмо.

## Шаг 11. Сага оформления и статусная модель заказа

**Материал:** [/patterns/saga/](https://vikulin-va.ru/patterns/saga/) · [/state-machines/what-is-a-state-machine/](https://vikulin-va.ru/state-machines/what-is-a-state-machine/) · [/case/order-service/](https://vikulin-va.ru/case/order-service/)

**Фазы:** 14 · 15 · 23 · 25 · раздел «Конечные автоматы»

**Даётся:** каркас Payment, схема переходов заказа, тесты сценариев отказа.

**Ученик:** сага «резерв → платёж → подтверждение» с компенсациями и мини
конечный автомат статусов заказа: разрешённые переходы, запрет остальных,
отмена по таймауту оплаты.

**Проверка:** отказ платежа откатывает резерв; недопустимый переход статуса
падает с понятной ошибкой, а не портит данные.

## Шаг 12. Keycloak, JWT, роли

**Материал:** [/keycloak/](https://vikulin-va.ru/keycloak/) · [/spring/security/](https://vikulin-va.ru/spring/security/) · [/auth-patterns/](https://vikulin-va.ru/auth-patterns/)

**Фазы:** 13 безопасность и авторизация · 11 сеть

**Даётся:** поднятый Keycloak с реалмом, роли покупателя, продавца, модератора.

**Ученик:** проверка токена без похода в Customer, доступ к операциям по ролям,
владелец карточки правит только свои товары.

**Проверка:** чужой токен получает 403, свой — 200; проверка работает при
недоступном Customer.

## Шаг 13. API Gateway и BFF

**Материал:** [/api-styles/](https://vikulin-va.ru/api-styles/) · [/microservices/](https://vikulin-va.ru/microservices/) · [/rest-api/](https://vikulin-va.ru/rest-api/)

**Фазы:** 12 · 15 · 23 · 11

**Даётся:** каркас Gateway на Spring Cloud Gateway и модуль BFF.

**Ученик:** маршруты и проверка токена на границе, лимит частоты на продавца со
счётчиками в Redis; в BFF — сборка экрана карточки одним запросом вместо трёх и
версионирование контракта.

**Проверка:** превышение лимита отдаёт 429; экран собирается одним вызовом.

## Шаг 14. Веб-клиент и продуктовые метрики

**Материал:** [/frontend/](https://vikulin-va.ru/frontend/) · [/product/](https://vikulin-va.ru/product/) · [/testing/](https://vikulin-va.ru/testing/)

**Фазы:** 28 продуктовое мышление · 20 качество

**Даётся:** каркас на React и TypeScript, компоненты и роутер.

**Ученик:** каталог, карточка, корзина, оформление и статус заказа поверх BFF;
события воронки и метрики: сколько дошло от карточки до оплаты и где отваливаются.

**Проверка:** сквозной сценарий покупки проходит в браузере; воронка считается.

## Шаг 15. Доставка: образы, Kubernetes, CI/CD, наблюдаемость

**Материал:** [/docker/](https://vikulin-va.ru/docker/) · [/kubernetes/](https://vikulin-va.ru/kubernetes/) · [/cicd/](https://vikulin-va.ru/cicd/) · [/observability/](https://vikulin-va.ru/observability/)

**Фазы:** 16 Docker · 17 Kubernetes · 18 облака · 19 CI/CD · 20 наблюдаемость

**Даётся:** черновики манифестов и пайплайна.

**Ученик:** образы сервисов, деплой в локальный кластер, корректное завершение,
пайплайн сборки и тестов, метрики и сквозной трейсинг запроса через три сервиса.

**Проверка:** пайплайн зелёный; трейс показывает путь запроса целиком.

---

## Боковые шаги

## Б1. Notification на Kotlin

**Материал:** [/kotlin/](https://vikulin-va.ru/kotlin/)

**Фаза:** 27 Kotlin для Java-разработчика

Сервис маленький и с понятной логикой — на нём разница языков видна сразу:
null-безопасность, data-классы, корутины вместо пула потоков. Контракт и тесты
остаются прежними, меняется только реализация.

## Б2. Поиск по каталогу на естественном языке

**Материал:** [/ai/](https://vikulin-va.ru/ai/) · [/product-engineer/](https://vikulin-va.ru/product-engineer/)

**Фаза:** 22 создание LLM-приложений

Запрос «недорогая беспроводная мышь для работы» превращается в фильтры каталога.
Отдельный сервис поверх поиска: промпт, ограничение стоимости, кэш ответов,
поведение при недоступности провайдера.
