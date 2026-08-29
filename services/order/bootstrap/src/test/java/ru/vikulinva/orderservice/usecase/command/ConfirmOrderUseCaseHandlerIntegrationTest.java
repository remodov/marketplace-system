package ru.vikulinva.orderservice.usecase.command;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.exception.OrderBelowMinimumException;
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
 * Интеграционный тест handler-а {@code ConfirmOrderUseCase} — UC-2.
 *
 * <p>Покрывает:
 * <ul>
 *   <li>Happy path: статус DRAFT → PENDING_PAYMENT, OrderConfirmed в Outbox.</li>
 *   <li>ABAC: чужой заказ → OrderNotFoundException (скрываем существование).</li>
 *   <li>Несуществующий заказ → OrderNotFoundException.</li>
 *   <li>Невалидный статус (повтор confirm) → OrderInvalidStateException.</li>
 *   <li>BR-013: сумма ниже 100 RUB → OrderBelowMinimumException.</li>
 * </ul>
 */
class ConfirmOrderUseCaseHandlerIntegrationTest extends PlatformBaseIntegrationTest {

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
    @DisplayName("happy path: переводит DRAFT → PENDING_PAYMENT и пишет OrderConfirmed в Outbox")
    void confirmOrder_happyPath() {
        var created = createDraftOrder("200.00", 1);

        var result = useCaseDispatcher.dispatch(
            new ConfirmOrderUseCase(created.orderId, created.customerId));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        List<OutboxRecord> outbox = dsl.selectFrom(OUTBOX).fetch();
        assertThat(outbox).hasSize(2);
        assertThat(outbox).anyMatch(r -> "OrderCreated".equals(r.getEventType()));
        assertThat(outbox).anyMatch(r -> "OrderConfirmed".equals(r.getEventType()));
    }

    @Test
    @DisplayName("ABAC: чужой заказ — OrderNotFoundException")
    void confirmOrder_otherCustomer_notFound() {
        var created = createDraftOrder("200.00", 1);
        var otherCustomer = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmOrderUseCase(created.orderId, otherCustomer)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("несуществующий заказ — OrderNotFoundException")
    void confirmOrder_missing_notFound() {
        var randomId = OrderId.of(UUID.randomUUID());
        var customer = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmOrderUseCase(randomId, customer)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("повторный confirm в PENDING_PAYMENT — OrderInvalidStateException")
    void confirmOrder_alreadyConfirmed_invalidState() {
        var created = createDraftOrder("200.00", 2);
        useCaseDispatcher.dispatch(new ConfirmOrderUseCase(created.orderId, created.customerId));

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmOrderUseCase(created.orderId, created.customerId)))
            .isInstanceOf(OrderInvalidStateException.class);
    }

    @Test
    @DisplayName("BR-013: сумма ниже 100 RUB — OrderBelowMinimumException")
    void confirmOrder_belowMinimum_rejected() {
        var created = createDraftOrder("50.00", 3);

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(
            new ConfirmOrderUseCase(created.orderId, created.customerId)))
            .isInstanceOf(OrderBelowMinimumException.class);
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
            "idem-confirm-" + idemSuffix,
            "hash-confirm-" + idemSuffix
        );
        var order = useCaseDispatcher.dispatch(useCase);
        return new CreatedOrder(order.getId(), customerId);
    }

    private record CreatedOrder(OrderId orderId, CustomerId customerId) {}
}
