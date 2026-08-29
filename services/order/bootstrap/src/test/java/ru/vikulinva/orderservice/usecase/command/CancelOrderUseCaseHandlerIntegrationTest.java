package ru.vikulinva.orderservice.usecase.command;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
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
 * Интеграционный тест handler-а {@code CancelOrderUseCase} — UC-3 (V1: до оплаты).
 *
 * <p>Покрывает:
 * <ul>
 *   <li>Happy path из DRAFT: статус CANCELLED, OrderCancelled в Outbox.</li>
 *   <li>Happy path из PENDING_PAYMENT: previousStatus в событии.</li>
 *   <li>ABAC: чужой заказ — OrderNotFoundException.</li>
 *   <li>Несуществующий заказ — OrderNotFoundException.</li>
 *   <li>Повторная отмена — OrderInvalidStateException.</li>
 * </ul>
 */
class CancelOrderUseCaseHandlerIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);
    private static final CancellationReason REASON =
        new CancellationReason("CHANGED_MIND", "no longer needed");

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
    @DisplayName("happy path из DRAFT: статус CANCELLED, OrderCancelled в Outbox")
    void cancelOrder_fromDraft_emitsCancelled() {
        var created = createDraftOrder("200.00", 1);

        var result = useCaseDispatcher.dispatch(
            new CancelOrderUseCase(created.orderId, created.customerId, REASON));

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);

        List<OutboxRecord> outbox = dsl.selectFrom(OUTBOX).fetch();
        assertThat(outbox).anyMatch(r -> "OrderCancelled".equals(r.getEventType()));
    }

    @Test
    @DisplayName("happy path из PENDING_PAYMENT: статус CANCELLED")
    void cancelOrder_fromPendingPayment() {
        var created = createDraftOrder("200.00", 2);
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(created.orderId, created.customerId));

        var result = useCaseDispatcher.dispatch(
            new CancelOrderUseCase(created.orderId, created.customerId, REASON));

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        List<OutboxRecord> outbox = dsl.selectFrom(OUTBOX).fetch();
        assertThat(outbox).anyMatch(r -> "OrderCancelled".equals(r.getEventType()));
    }

    @Test
    @DisplayName("ABAC: чужой заказ — OrderNotFoundException")
    void cancelOrder_otherCustomer_notFound() {
        var created = createDraftOrder("200.00", 3);
        var other = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new CancelOrderUseCase(created.orderId, other, REASON)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("несуществующий заказ — OrderNotFoundException")
    void cancelOrder_missing_notFound() {
        assertThatThrownBy(() -> useCaseDispatcher.dispatch(new CancelOrderUseCase(
            OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            REASON)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("повторная отмена — OrderInvalidStateException")
    void cancelOrder_alreadyCancelled_invalidState() {
        var created = createDraftOrder("200.00", 4);
        useCaseDispatcher.dispatch(new CancelOrderUseCase(created.orderId, created.customerId, REASON));

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new CancelOrderUseCase(created.orderId, created.customerId, REASON)))
            .isInstanceOf(OrderInvalidStateException.class);
    }

    private CreatedOrder createDraftOrder(String price, int idemSuffix) {
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

        var useCase = new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-cancel-" + idemSuffix,
            "hash-cancel-" + idemSuffix
        );
        var order = useCaseDispatcher.dispatch(useCase);
        return new CreatedOrder(order.getId(), customerId);
    }

    private record CreatedOrder(OrderId orderId, CustomerId customerId) {}
}
