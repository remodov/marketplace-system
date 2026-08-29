package ru.vikulinva.orderservice.usecase.command;

import org.jooq.DSLContext;
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
import ru.vikulinva.orderservice.usecase.command.exception.OrderInvalidStateException;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Интеграционные тесты на полный жизненный цикл: Pay → Ship → Deliver
 * (UC-6, UC-7, UC-8). Также проверяем идемпотентность вебхука Pay.
 */
class OrderLifecycleIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("полный путь: Create → Confirm → Pay → Ship → Deliver")
    void fullHappyPath() {
        var ctx = createConfirmedOrder("200.00", 1);

        // Pay
        UUID paymentId = UUID.randomUUID();
        var paid = useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, paymentId));
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paymentId()).isEqualTo(paymentId);
        assertThat(paid.paidAt()).isNotNull();

        // Ship
        var shipped = useCaseDispatcher.dispatch(
            new MarkShippedUseCase(ctx.orderId, ctx.sellerId, "TRACK-12345"));
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.shippedAt()).isNotNull();

        // Deliver
        var delivered = useCaseDispatcher.dispatch(
            new ConfirmDeliveryUseCase(ctx.orderId, ctx.customerId));
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.deliveredAt()).isNotNull();

        // Все события в outbox
        var events = dsl.select(OUTBOX.EVENT_TYPE).from(OUTBOX).fetch(OUTBOX.EVENT_TYPE);
        assertThat(events).contains("OrderCreated", "OrderConfirmed", "OrderPaid", "OrderShipped", "OrderDelivered");
    }

    @Test
    @DisplayName("PayOrder идемпотентен: повторный вебхук с тем же paymentId — no-op")
    void pay_idempotentReplay() {
        var ctx = createConfirmedOrder("200.00", 2);
        UUID paymentId = UUID.randomUUID();

        var first = useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, paymentId));
        var second = useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, paymentId));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.status()).isEqualTo(OrderStatus.PAID);

        long paidEvents = dsl.fetchCount(dsl.selectOne().from(OUTBOX).where(OUTBOX.EVENT_TYPE.eq("OrderPaid")));
        assertThat(paidEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("Pay из DRAFT — OrderInvalidStateException")
    void pay_fromDraft_invalidState() {
        var ctx = createDraftOrder("200.00", 3);

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new PayOrderUseCase(ctx.orderId, UUID.randomUUID())))
            .isInstanceOf(OrderInvalidStateException.class);
    }

    @Test
    @DisplayName("Ship: ABAC — чужой продавец видит OrderNotFoundException")
    void ship_otherSeller_notFound() {
        var ctx = createPaidOrder("200.00", 4);
        var otherSeller = SellerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new MarkShippedUseCase(ctx.orderId, otherSeller, "TRACK-99")))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Deliver: ABAC — чужой покупатель видит OrderNotFoundException")
    void deliver_otherCustomer_notFound() {
        var ctx = createPaidOrder("200.00", 5);
        useCaseDispatcher.dispatch(new MarkShippedUseCase(ctx.orderId, ctx.sellerId, "TRACK"));
        var otherCustomer = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmDeliveryUseCase(ctx.orderId, otherCustomer)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Deliver из PAID (без Ship) — OrderInvalidStateException")
    void deliver_fromPaid_invalidState() {
        var ctx = createPaidOrder("200.00", 6);

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmDeliveryUseCase(ctx.orderId, ctx.customerId)))
            .isInstanceOf(OrderInvalidStateException.class);
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
            "idem-life-" + idemSuffix,
            "hash-life-" + idemSuffix
        ));
        return new OrderContext(order.getId(), customerId, sellerId);
    }

    private OrderContext createConfirmedOrder(String price, int idemSuffix) {
        var ctx = createDraftOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(ctx.orderId, ctx.customerId));
        return ctx;
    }

    private OrderContext createPaidOrder(String price, int idemSuffix) {
        var ctx = createConfirmedOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, UUID.randomUUID()));
        return ctx;
    }

    private record OrderContext(OrderId orderId, CustomerId customerId, SellerId sellerId) {}
}
