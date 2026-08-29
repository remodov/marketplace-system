package ru.vikulinva.orderservice.usecase.query;

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
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
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

/**
 * Интеграционный тест handler-а {@code GetOrderByIdQuery} — UC-4.
 */
class GetOrderByIdQueryHandlerIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("happy path: владелец получает свой заказ")
    void getOrder_owner() {
        var created = createDraftOrder("200.00", 1);

        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(created.orderId, created.customerId));

        assertThat(order.getId()).isEqualTo(created.orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.items()).hasSize(1);
    }

    @Test
    @DisplayName("ABAC: чужой заказ — OrderNotFoundException")
    void getOrder_otherCustomer_notFound() {
        var created = createDraftOrder("200.00", 2);
        var other = CustomerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(new GetOrderByIdQuery(created.orderId, other)))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("несуществующий заказ — OrderNotFoundException")
    void getOrder_missing() {
        assertThatThrownBy(() -> useCaseDispatcher.dispatch(new GetOrderByIdQuery(
            OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()))))
            .isInstanceOf(OrderNotFoundException.class);
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
            "idem-get-" + idemSuffix,
            "hash-get-" + idemSuffix
        );
        var order = useCaseDispatcher.dispatch(useCase);
        return new CreatedOrder(order.getId(), customerId);
    }

    private record CreatedOrder(OrderId orderId, CustomerId customerId) {}
}
