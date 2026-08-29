# Payment: платежи на голом JDBC

Третий способ работы с базой в этой системе — намеренно. В `catalog-starter`
Spring Data JPA, в `catalog` и `order` — jOOQ с генерацией из схемы, здесь —
`JdbcTemplate`: SQL руками, маппинг строки в объект руками.

Так видно, что делают за тебя остальные: транзакции, маппинг, генерация запросов.
И видно, чего они стоят — здесь нет ни кодогенерации, ни кеша первого уровня,
зато каждый запрос виден целиком.

## Запустить

```bash
docker compose -f ../../infra/compose.yaml up -d postgres-payment
../../gradlew bootRun
```

Порт 8085, схема накатывается из `src/main/resources/schema.sql` при старте.

```bash
curl -s -X POST localhost:8085/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"orderId":"<uuid>","amount":1990.00,"currency":"RUB"}'
curl -s -X POST localhost:8085/api/v1/payments/<id>/capture
curl -s -X POST localhost:8085/api/v1/payments/<id>/refund
```

## Что внутри

| файл | зачем |
|---|---|
| `Payment` | статус платежа и автомат переходов: разрешённое перечислено, остальное запрещено |
| `PaymentRepository` | голый JDBC: `JdbcTemplate`, свой `RowMapper` |
| `PaymentService` | сценарии саги: авторизация, списание, возврат; повторы безопасны |
| `PaymentController` | REST: создать, посмотреть, списать, вернуть |
| `PaymentExceptionHandler` | недопустимый переход → 409, неизвестный платёж → 404 |

Автомат живёт в `Payment.Status.canMoveTo`, а не в сервисе: правило одно на все
входы, и обойти его нельзя.

## Тесты

```bash
../../gradlew test
```

Идут на H2, Docker не нужен. Переходы статусов проверяются отдельно, без Spring —
это чистое правило, ему база не нужна.

## Что почитать

- [Сага](https://vikulin-va.ru/distributed-patterns-style-guide/saga/) и [компенсации](https://vikulin-va.ru/distributed-patterns-style-guide/compensation/) — зачем платежу возврат.
- [Конечные автоматы](https://vikulin-va.ru/state-machines/) — почему статус это автомат, а не строка.
- [Идемпотентность](https://vikulin-va.ru/distributed-patterns-style-guide/idempotency/) — почему повторный возврат не ошибка.
- [JDBC и Spring](https://vikulin-va.ru/spring/data-jpa/) — что делает JPA поверх того, что здесь написано руками.
