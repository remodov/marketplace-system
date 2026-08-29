---
type: service
context: order-service
tier: C
ucp-level: 3
tags:
  - service
  - tech/java
  - tech/spring-boot
  - tech/jooq
  - tech/postgres
  - tech/kafka
  - tech/ddd
  - ucp/level-3
  - tier/C
  - bc/order
  - case/marketplace
---

# Order Service — Use Case спецификация (Tier C)

> Индекс. Спека разбита на 17 файлов — по одному на раздел. Каждый раздел открывается по ссылке из таблицы ниже.

**Сервис:** оформление заказов на маркетплейсе. Корень — агрегат `Order`, поверх — Saga-оркестрация платежа, резервирования остатка и доставки. Tier C / UCP Level 3 (DDD).

## Оглавление

| №   | Раздел                            | Файл                                      | Кто заполняет     |
| --- | --------------------------------- | ----------------------------------------- | ----------------- |
| 01  | Bounded Context                   | [01-order-service-context](01-order-service-context.md)         | БА + Архитектор   |
| 02  | Ubiquitous Language               | [02-order-service-language](02-order-service-language.md)       | БА                |
| 03  | Domain Model                      | [03-order-service-model](03-order-service-model.md)             | Архитектор + Dev  |
| 04  | Жизненный цикл и состояния        | [04-order-service-lifecycle](04-order-service-lifecycle.md)     | БА + Архитектор   |
| 05  | Роли и права                      | [05-order-service-roles](05-order-service-roles.md)             | БА                |
| 06  | Бизнес-правила (BR)               | [06-order-service-rules](06-order-service-rules.md)             | БА + Архитектор   |
| 07  | Commands                          | [07-order-service-commands](07-order-service-commands.md)       | Архитектор + Dev  |
| 08  | Domain Events                     | [08-order-service-events](08-order-service-events.md)           | Архитектор        |
| 09  | Queries                           | [09-order-service-queries](09-order-service-queries.md)         | Архитектор + Dev  |
| 10  | Use Cases                         | [10-order-service-use-cases](10-order-service-use-cases.md)     | БА                |
| 11  | UI-спецификация                   | [11-order-service-ui](11-order-service-ui.md)                   | БА                |
| 12  | Saga / Process Manager            | [12-order-service-sagas](12-order-service-sagas.md)             | Архитектор        |
| 13  | Каталог ошибок                    | [13-order-service-errors](13-order-service-errors.md)           | Dev               |
| 14  | Интеграции                        | `14-order-service-integrations/` (папка)  | Архитектор        |
| 15  | Критерии приёмки (AC)             | [15-order-service-acceptance](15-order-service-acceptance.md)   | БА + QA           |
| 16  | Нефункциональные требования       | [16-order-service-nfr](16-order-service-nfr.md)                 | Архитектор        |
| 17  | Стек технологий                   | [17-order-service-stack](17-order-service-stack.md)             | Архитектор + Dev  |

## Интеграции

Все рёбра — в папке `14-order-service-integrations/`, по файлу на интеграцию.

## Стек

Spring Boot · jOOQ · PostgreSQL · Kafka · `usecase-pattern` · `ddd-building-blocks`. Детали — [17-order-service-stack](17-order-service-stack.md).

## Связанные артефакты

- [Кейс: маркетплейс](/case/) — бизнес-описание, на котором строится спека.
- [Use Case спецификация: универсальный шаблон](/use-case-pattern/spec-template/) — структура 16 разделов.
- [Тактические паттерны DDD](/domain-driven-design/tactical-patterns/) — формат агрегатов и событий.
- [Распределённые паттерны](/distributed-patterns/) — Outbox, Saga, Idempotent Consumer.
