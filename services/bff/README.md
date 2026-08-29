# BFF: экран собирается одним запросом

Backend for frontend — тонкий слой на границе. Клиент просит **экран**, а не
три ресурса из трёх сервисов: мобильному приложению три круговые задержки
дороже, чем одна.

Здесь же живёт то, что положено границе: лимит частоты и внятный ответ, когда
сосед не отвечает.

## Запустить

```bash
docker compose -f ../../infra/compose.yaml up -d redis
../../gradlew bootRun
```

Порт 8090, адреса соседей — в `application.yml` (или через переменные
`ORDER_URL`, `CATALOG_URL`, `PAYMENT_URL`).

```bash
curl -s -H 'X-Client-Id: demo' localhost:8090/api/v1/screens/order/<orderId>
```

## Что внутри

| файл | зачем |
|---|---|
| `OrderScreenService` | сборка экрана: заказ, карточки товаров и статус платежа |
| `OrderScreenController` | одна ручка на весь экран |
| `RateLimiter` | счётчик запросов в Redis — общий для всех инстансов |
| `RateLimitFilter` | 429 и `Retry-After` при превышении |
| `ScreenExceptionHandler` | недоступный сосед → 502, а не пятисотка без объяснений |

Карточки товаров и статус платежа запрашиваются параллельно: экран ждёт самый
медленный ответ, а не сумму всех.

## Тесты

```bash
docker compose -f ../../infra/compose.yaml up -d redis
../../gradlew test
```

Соседи подменены заглушками, Redis нужен настоящий — счётчик и должен быть
общим, а не в памяти процесса.

## Что почитать

- [Стили API](https://vikulin-va.ru/api-styles/) — почему BFF, а не «универсальный» ресурс.
- [Монолит и микросервисы](https://vikulin-va.ru/architecture-choice/monolith-vs-microservices/) — откуда берётся проблема трёх запросов.
- [Redis](https://vikulin-va.ru/redis/) — счётчики и время жизни ключа.
- [Устойчивость](https://vikulin-va.ru/patterns/resilience/) — что отвечать, когда сосед молчит.
