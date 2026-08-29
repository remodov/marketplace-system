package ru.vikulinva.orderservice.scheduler;

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
import ru.vikulinva.orderservice.usecase.command.ConfirmDeliveryUseCase;
import ru.vikulinva.orderservice.usecase.command.ConfirmOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.MarkShippedUseCase;
import ru.vikulinva.orderservice.usecase.command.PayOrderUseCase;
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
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Integration-тесты для системных планировщиков ExpireUnpaid + CloseDelivered.
 * Время мокаем через {@code @MockitoBean DateTimeService} (TS-7), затем
 * вызываем {@code .tick()} — без реального ожидания cron/fixedDelay.
 */
class SchedulerIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private ExpireUnpaidScheduler expireUnpaidScheduler;

    @Autowired
    private CloseDeliveredScheduler closeDeliveredScheduler;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("ExpireUnpaid: PENDING_PAYMENT старше 15 мин → EXPIRED + OrderExpired")
    void expireUnpaid_oldOrderExpires() {
        // Заказ создан в 10:00, теперь 10:20 (15 мин уже прошло)
        var ctx = createConfirmedOrder("200.00", 1, Instant.parse("2026-04-01T10:00:00Z"));
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:20:00Z"));

        expireUnpaidScheduler.tick();

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(dsl.fetchCount(dsl.selectOne().from(OUTBOX).where(OUTBOX.EVENT_TYPE.eq("OrderExpired"))))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("ExpireUnpaid: свежий заказ не трогается")
    void expireUnpaid_freshOrderUntouched() {
        var ctx = createConfirmedOrder("200.00", 2, Instant.parse("2026-04-01T10:00:00Z"));
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:05:00Z"));

        expireUnpaidScheduler.tick();

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("CloseDelivered: DELIVERED старше 14 дней → COMPLETED + OrderCompleted")
    void closeDelivered_staleOrderCompletes() {
        var ctx = createDeliveredOrder("200.00", 3,
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-05T15:00:00Z"));
        // 14+ дней спустя
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-20T04:00:00Z"));

        closeDeliveredScheduler.tick();

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(dsl.fetchCount(dsl.selectOne().from(OUTBOX).where(OUTBOX.EVENT_TYPE.eq("OrderCompleted"))))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("CloseDelivered: свежая доставка (< 14 дней) не трогается")
    void closeDelivered_freshDeliveryUntouched() {
        var ctx = createDeliveredOrder("200.00", 4,
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-05T15:00:00Z"));
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-10T04:00:00Z"));

        closeDeliveredScheduler.tick();

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(ctx.orderId, ctx.customerId));
        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    private OrderContext createConfirmedOrder(String price, int idemSuffix, Instant createdAt) {
        var ctx = createDraftOrder(price, idemSuffix, createdAt);
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(ctx.orderId, ctx.customerId));
        return ctx;
    }

    private OrderContext createDeliveredOrder(String price, int idemSuffix,
                                                Instant createdAt, Instant deliveredAt) {
        var ctx = createConfirmedOrder(price, idemSuffix, createdAt);
        // pay/ship/deliver в монотонном времени
        given(dateTimeService.now()).willReturn(createdAt.plusSeconds(60));
        useCaseDispatcher.dispatch(new PayOrderUseCase(ctx.orderId, UUID.randomUUID()));
        given(dateTimeService.now()).willReturn(createdAt.plusSeconds(120));
        useCaseDispatcher.dispatch(new MarkShippedUseCase(ctx.orderId, ctx.sellerId, "TRACK"));
        given(dateTimeService.now()).willReturn(deliveredAt);
        useCaseDispatcher.dispatch(new ConfirmDeliveryUseCase(ctx.orderId, ctx.customerId));
        return ctx;
    }

    private OrderContext createDraftOrder(String price, int idemSuffix, Instant createdAt) {
        var customerId = CustomerId.of(UUID.randomUUID());
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderUuid = UUID.randomUUID();
        var orderItemUuid = UUID.randomUUID();

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderUuid : orderItemUuid);
        given(dateTimeService.now()).willReturn(createdAt);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "%s", "currency": "RUB" }
                """.formatted(productId.value(), price))));

        var order = useCaseDispatcher.dispatch(new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-sched-" + idemSuffix,
            "hash-sched-" + idemSuffix
        ));
        return new OrderContext(order.getId(), customerId, sellerId);
    }

    private record OrderContext(OrderId orderId, CustomerId customerId, SellerId sellerId) {}
}
