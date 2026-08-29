package ru.vikulinva.orderservice.usecase.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
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
 * Интеграционный тест handler-а {@code ListMyOrdersQuery} — UC-5.
 */
class ListMyOrdersQueryHandlerIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("ListSellerOrders: возвращает только заказы конкретного продавца")
    void list_seller_filtersBySeller() {
        var sellerA = SellerId.of(UUID.randomUUID());
        var sellerB = SellerId.of(UUID.randomUUID());
        createOrderForSeller(sellerA, "100.00", Instant.parse("2026-04-01T10:00:00Z"), 100);
        createOrderForSeller(sellerA, "200.00", Instant.parse("2026-04-02T10:00:00Z"), 101);
        createOrderForSeller(sellerB, "300.00", Instant.parse("2026-04-03T10:00:00Z"), 102);

        var result = useCaseDispatcher.dispatch(
            new ru.vikulinva.orderservice.usecase.query.ListSellerOrdersQuery(sellerA, null, 0, 20));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).allMatch(s -> s.sellerId().equals(sellerA));
    }

    private void createOrderForSeller(SellerId sellerId, String price, Instant when, int idemSuffix) {
        var customerId = CustomerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderUuid = UUID.randomUUID();
        var orderItemUuid = UUID.randomUUID();

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderUuid : orderItemUuid);
        given(dateTimeService.now()).willReturn(when);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "%s", "currency": "RUB" }
                """.formatted(productId.value(), price))));

        useCaseDispatcher.dispatch(new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-list-seller-" + idemSuffix,
            "hash-list-seller-" + idemSuffix
        ));
    }

    @Test
    @DisplayName("happy path: возвращает только заказы покупателя, отсортированные по createdAt DESC")
    void list_filtersAndSorts() {
        var customer = CustomerId.of(UUID.randomUUID());
        var otherCustomer = CustomerId.of(UUID.randomUUID());
        createOrder(customer, "100.00", Instant.parse("2026-04-01T10:00:00Z"), 1);
        createOrder(customer, "200.00", Instant.parse("2026-04-02T10:00:00Z"), 2);
        createOrder(otherCustomer, "300.00", Instant.parse("2026-04-03T10:00:00Z"), 3);

        var result = useCaseDispatcher.dispatch(
            new ListMyOrdersQuery(customer, null, 0, 20));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        // newer first
        assertThat(result.items().get(0).total().amount().toPlainString()).isEqualTo("200.00");
        assertThat(result.items().get(1).total().amount().toPlainString()).isEqualTo("100.00");
        assertThat(result.items()).allMatch(s -> s.customerId().equals(customer));
        assertThat(result.items().get(0).itemsCount()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("фильтр по статусу: возвращает только заказы в нужном статусе")
    void list_filterByStatus() {
        var customer = CustomerId.of(UUID.randomUUID());
        createOrder(customer, "100.00", Instant.parse("2026-04-01T10:00:00Z"), 4);
        createOrder(customer, "200.00", Instant.parse("2026-04-02T10:00:00Z"), 5);

        var draft = useCaseDispatcher.dispatch(
            new ListMyOrdersQuery(customer, OrderStatus.DRAFT, 0, 20));
        assertThat(draft.total()).isEqualTo(2);

        var paid = useCaseDispatcher.dispatch(
            new ListMyOrdersQuery(customer, OrderStatus.PAID, 0, 20));
        assertThat(paid.total()).isEqualTo(0);
        assertThat(paid.items()).isEmpty();
    }

    @Test
    @DisplayName("пагинация: page=1, size=1 возвращает второй заказ и hasNext=false")
    void list_pagination() {
        var customer = CustomerId.of(UUID.randomUUID());
        createOrder(customer, "100.00", Instant.parse("2026-04-01T10:00:00Z"), 6);
        createOrder(customer, "200.00", Instant.parse("2026-04-02T10:00:00Z"), 7);

        var p0 = useCaseDispatcher.dispatch(new ListMyOrdersQuery(customer, null, 0, 1));
        assertThat(p0.items()).hasSize(1);
        assertThat(p0.total()).isEqualTo(2);
        assertThat(p0.hasNext()).isTrue();
        // first page is the newest
        assertThat(p0.items().get(0).total().amount().toPlainString()).isEqualTo("200.00");

        var p1 = useCaseDispatcher.dispatch(new ListMyOrdersQuery(customer, null, 1, 1));
        assertThat(p1.items()).hasSize(1);
        assertThat(p1.items().get(0).total().amount().toPlainString()).isEqualTo("100.00");
        assertThat(p1.hasNext()).isFalse();
    }

    private void createOrder(CustomerId customer, String price, Instant when, int idemSuffix) {
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderUuid = UUID.randomUUID();
        var orderItemUuid = UUID.randomUUID();

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderUuid : orderItemUuid);
        given(dateTimeService.now()).willReturn(when);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "%s", "currency": "RUB" }
                """.formatted(productId.value(), price))));

        useCaseDispatcher.dispatch(new CreateOrderUseCase(
            customer,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-list-" + idemSuffix,
            "hash-list-" + idemSuffix
        ));
    }
}
