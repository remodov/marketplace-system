package ru.vikulinva.orderservice.usecase.command;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.exception.PaymentUnavailableException;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Integration-тест refund-саги: отмена оплаченного заказа должна
 * синхронно дёрнуть Payment Service и сохранить refundId в событии
 * OrderCancelled.
 */
class CancelPaidOrderRefundSagaIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
        payment.resetAll();
    }

    @Test
    @DisplayName("отмена PAID-заказа: вызывается Payment, статус CANCELLED, OrderCancelled содержит refundId")
    void cancelPaid_callsPaymentAndCancels() {
        var ctx = createPaidOrder("200.00", 1);
        UUID refundId = UUID.randomUUID();
        payment.stubFor(post(urlEqualTo("/api/v1/refunds"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"refundId\":\"" + refundId + "\",\"status\":\"REQUESTED\"}")));

        var cancelled = useCaseDispatcher.dispatch(new CancelOrderUseCase(
            ctx.orderId, ctx.customerId,
            new CancellationReason("CHANGED_MIND", null)));

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        payment.verify(postRequestedFor(urlEqualTo("/api/v1/refunds"))
            .withHeader("Idempotency-Key", equalTo("refund-" + ctx.orderId.value())));

        // OrderCancelled event published with refundId
        var lastCancelled = dsl.selectFrom(OUTBOX)
            .where(OUTBOX.EVENT_TYPE.eq("OrderCancelled"))
            .orderBy(OUTBOX.OCCURRED_AT.desc())
            .fetchOne();
        assertThat(lastCancelled).isNotNull();
        assertThat(lastCancelled.getPayload().data()).contains(refundId.toString());
    }

    @Test
    @DisplayName("Payment недоступен — отмена не происходит, транзакция откатывается, заказ остаётся PAID")
    void cancelPaid_paymentDown_rollback() {
        var ctx = createPaidOrder("200.00", 2);
        payment.stubFor(post(urlEqualTo("/api/v1/refunds"))
            .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(new CancelOrderUseCase(
            ctx.orderId, ctx.customerId,
            new CancellationReason("X", null))))
            .isInstanceOf(PaymentUnavailableException.class);

        // Заказ всё ещё PAID — транзакция откатилась.
        var still = useCaseDispatcher.dispatch(
            new ru.vikulinva.orderservice.usecase.query.GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(still.status()).isEqualTo(OrderStatus.PAID);
    }

    private OrderContext createPaidOrder(String price, int idemSuffix) {
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
            "idem-refund-" + idemSuffix,
            "hash-refund-" + idemSuffix
        ));
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(order.getId(), customerId));
        useCaseDispatcher.dispatch(new PayOrderUseCase(order.getId(), UUID.randomUUID()));
        return new OrderContext(order.getId(), customerId, sellerId);
    }

    private record OrderContext(OrderId orderId, CustomerId customerId, SellerId sellerId) {}
}
