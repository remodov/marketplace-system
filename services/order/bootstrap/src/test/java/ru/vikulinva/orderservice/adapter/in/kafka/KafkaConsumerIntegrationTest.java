package ru.vikulinva.orderservice.adapter.in.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.ConfirmOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
import ru.vikulinva.orderservice.usecase.query.GetOrderByIdQuery;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Тестируем Kafka-консьюмеры путём прямого вызова {@code onMessage(record)} —
 * без поднятия embedded Kafka. Это покрывает логику парсинга, идемпотентность
 * через processed_events и интеграцию с UseCase.
 */
class KafkaConsumerIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    @Autowired
    private InventoryEventConsumer inventoryEventConsumer;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("PaymentCompleted переводит заказ в PAID")
    void paymentCompleted_paysOrder() {
        var ctx = createConfirmedOrder("200.00", 1);
        UUID paymentId = UUID.randomUUID();

        paymentEventConsumer.onMessage(paymentCompletedRecord(ctx.orderId, paymentId));

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("PaymentCompleted с тем же event-id — идемпотентен (повтор не создаёт второе OrderPaid)")
    void paymentCompleted_idempotent() {
        var ctx = createConfirmedOrder("200.00", 2);
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        var record = paymentCompletedRecord(ctx.orderId, paymentId, eventId);
        paymentEventConsumer.onMessage(record);
        paymentEventConsumer.onMessage(record);   // повтор

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Игнорирует сообщения с event-type, отличным от PaymentCompleted")
    void paymentConsumer_ignoresOtherEventTypes() {
        var ctx = createConfirmedOrder("200.00", 3);
        var record = recordWithType("PaymentRefunded",
            "{\"orderId\":\"" + ctx.orderId.value() + "\",\"paymentId\":\"" + UUID.randomUUID() + "\"}");

        paymentEventConsumer.onMessage(record);

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("ReservationFailed отменяет заказ системной причиной")
    void reservationFailed_cancelsOrder() {
        var ctx = createConfirmedOrder("200.00", 4);

        inventoryEventConsumer.onMessage(reservationFailedRecord(ctx.orderId, ctx.customerId));

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("ItemReserved не меняет состояние (no-op)")
    void itemReserved_noop() {
        var ctx = createConfirmedOrder("200.00", 5);
        var record = recordWithTopicAndType("marketplace.inventory.v1", "ItemReserved",
            "{\"orderId\":\"" + ctx.orderId.value() + "\"}");

        inventoryEventConsumer.onMessage(record);

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private ConsumerRecord<String, String> paymentCompletedRecord(OrderId orderId, UUID paymentId) {
        return paymentCompletedRecord(orderId, paymentId, UUID.randomUUID());
    }

    private ConsumerRecord<String, String> paymentCompletedRecord(OrderId orderId, UUID paymentId, UUID eventId) {
        String json = "{\"orderId\":\"%s\",\"paymentId\":\"%s\"}".formatted(orderId.value(), paymentId);
        var record = recordWithTopicAndType("marketplace.payments.v1", "PaymentCompleted", json);
        record.headers().remove("event-id");
        record.headers().add("event-id", eventId.toString().getBytes());
        return record;
    }

    private ConsumerRecord<String, String> reservationFailedRecord(OrderId orderId, CustomerId customerId) {
        String json = "{\"orderId\":\"%s\",\"customerId\":\"%s\"}".formatted(orderId.value(), customerId.value());
        return recordWithTopicAndType("marketplace.inventory.v1", "ReservationFailed", json);
    }

    private ConsumerRecord<String, String> recordWithType(String eventType, String payload) {
        return recordWithTopicAndType("marketplace.payments.v1", eventType, payload);
    }

    private ConsumerRecord<String, String> recordWithTopicAndType(String topic, String eventType, String payload) {
        var record = new ConsumerRecord<>(topic, 0, 0L, "key", payload);
        record.headers().add("event-id", UUID.randomUUID().toString().getBytes());
        record.headers().add("event-type", eventType.getBytes());
        return record;
    }

    private OrderContext createConfirmedOrder(String price, int idemSuffix) {
        var ctx = createDraftOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(ctx.orderId, ctx.customerId));
        return ctx;
    }

    private OrderContext createDraftOrder(String price, int idemSuffix) {
        var customerId = CustomerId.of(UUID.randomUUID());
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderUuid = UUID.randomUUID();
        var orderItemUuid = UUID.randomUUID();
        var now = Instant.parse("2026-04-01T10:00:00Z");

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderUuid : orderItemUuid);
        given(dateTimeService.now()).willReturn(now);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "%s", "currency": "RUB" }
                """.formatted(productId.value(), price))));

        var order = useCaseDispatcher.dispatch(new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-kafka-" + idemSuffix,
            "hash-kafka-" + idemSuffix
        ));
        return new OrderContext(order.getId(), customerId, sellerId);
    }

    private record OrderContext(OrderId orderId, CustomerId customerId, SellerId sellerId) {}
}
