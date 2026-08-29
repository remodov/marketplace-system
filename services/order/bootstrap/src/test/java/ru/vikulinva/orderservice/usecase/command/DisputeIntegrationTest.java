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

class DisputeIntegrationTest extends PlatformBaseIntegrationTest {

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
    @DisplayName("happy path: открытие спора DELIVERED→DISPUTE и закрытие в COMPLETED")
    void openAndResolveAsCompleted() {
        var ctx = createDeliveredOrder("200.00", 1);

        useCaseDispatcher.dispatch(
            new OpenDisputeUseCase(ctx.orderId, ctx.customerId, "Поломан при доставке"));

        var resolved = useCaseDispatcher.dispatch(
            new ResolveDisputeUseCase(ctx.orderId, OrderStatus.COMPLETED, "Доказательств повреждения нет"));

        assertThat(resolved.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(dsl.select(OUTBOX.EVENT_TYPE).from(OUTBOX).fetch(OUTBOX.EVENT_TYPE))
            .contains("DisputeOpened", "DisputeResolved");
    }

    @Test
    @DisplayName("закрытие спора в REFUNDED — финальный статус и событие")
    void resolveAsRefunded() {
        var ctx = createDeliveredOrder("200.00", 2);
        useCaseDispatcher.dispatch(
            new OpenDisputeUseCase(ctx.orderId, ctx.customerId, "Не пришло"));

        var resolved = useCaseDispatcher.dispatch(
            new ResolveDisputeUseCase(ctx.orderId, OrderStatus.REFUNDED, "Подтверждено отсутствие"));

        assertThat(resolved.status()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("OpenDispute: ABAC — чужой покупатель видит OrderNotFoundException")
    void openDispute_otherCustomer_notFound() {
        var ctx = createDeliveredOrder("200.00", 3);
        var other = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new OpenDisputeUseCase(ctx.orderId, other, "wrong-product")))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("OpenDispute из не-DELIVERED — OrderInvalidStateException")
    void openDispute_fromShipped_invalidState() {
        var ctx = createShippedOrder("200.00", 4);

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new OpenDisputeUseCase(ctx.orderId, ctx.customerId, "x")))
            .isInstanceOf(OrderInvalidStateException.class);
    }

    @Test
    @DisplayName("ResolveDispute из не-DISPUTE — OrderInvalidStateException")
    void resolveDispute_fromCompleted_invalidState() {
        var ctx = createDeliveredOrder("200.00", 5);
        useCaseDispatcher.dispatch(
            new OpenDisputeUseCase(ctx.orderId, ctx.customerId, "x"));
        useCaseDispatcher.dispatch(
            new ResolveDisputeUseCase(ctx.orderId, OrderStatus.COMPLETED, "ok"));

        // повторный вызов на уже COMPLETED заказе
        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ResolveDisputeUseCase(ctx.orderId, OrderStatus.REFUNDED, "again")))
            .isInstanceOf(OrderInvalidStateException.class);
    }

    @Test
    @DisplayName("ResolveDispute с finalStatus != COMPLETED|REFUNDED — IllegalArgumentException на этапе UseCase")
    void resolveDispute_invalidFinalStatus() {
        assertThatThrownBy(() -> new ResolveDisputeUseCase(
            OrderId.of(UUID.randomUUID()), OrderStatus.PAID, "wrong"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private OrderContext createDeliveredOrder(String price, int idemSuffix) {
        var ctx = createShippedOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new ConfirmDeliveryUseCase(ctx.orderId, ctx.customerId));
        return ctx;
    }

    private OrderContext createShippedOrder(String price, int idemSuffix) {
        var ctx = createPaidOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new MarkShippedUseCase(ctx.orderId, ctx.sellerId, "TRACK-" + idemSuffix));
        return ctx;
    }

    private OrderContext createPaidOrder(String price, int idemSuffix) {
        var ctx = createConfirmedOrder(price, idemSuffix);
        useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, UUID.randomUUID()));
        return ctx;
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
            "idem-disp-" + idemSuffix,
            "hash-disp-" + idemSuffix
        ));
        return new OrderContext(order.getId(), customerId, sellerId);
    }

    private record OrderContext(OrderId orderId, CustomerId customerId, SellerId sellerId) {}
}
