package ru.vikulinva.orderservice.usecase.command;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.exception.IdempotencyKeyConflictException;
import ru.vikulinva.orderservice.usecase.command.exception.MultiSellerNotSupportedException;
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
 * Интеграционный тест handler-а {@code CreateOrderUseCase} — TS-1, TS-2, TS-13.
 *
 * <p>Покрывает:
 * <ul>
 *   <li>Happy path: заказ создан в DRAFT, событие OrderCreated в Outbox.</li>
 *   <li>BR-014: multi-seller отклоняется ещё до Catalog (фейл-fast).</li>
 *   <li>BR-010: повторный вызов с тем же idempotencyKey + телом возвращает прежний orderId;
 *       с тем же ключом и другим хешем — IdempotencyKeyConflictException.</li>
 * </ul>
 */
class CreateOrderUseCaseHandlerIntegrationTest extends PlatformBaseIntegrationTest {

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
    @DisplayName("happy path: создаёт заказ в DRAFT и пишет OrderCreated в Outbox")
    void createOrder_happyPath() {
        // Arrange
        var customerId = CustomerId.of(UUID.randomUUID());
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderId = UUID.randomUUID();
        var orderItemId = UUID.randomUUID();
        var now = Instant.parse("2026-04-01T10:00:00Z");

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderId : orderItemId);
        given(dateTimeService.now()).willReturn(now);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson(productJson(productId.value(), "100.00"))));

        var useCase = new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(2))),
            new Address("RU", "Moscow", "Tverskaya 1", "125009", null),
            "idem-key-1",
            "request-hash-1"
        );

        // Act
        var result = useCaseDispatcher.dispatch(useCase);

        // Assert
        assertThat(result.getId().value()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(result.items()).hasSize(1);

        // Outbox содержит OrderCreated
        List<OutboxRecord> outboxRows = dsl.selectFrom(OUTBOX).fetch();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("OrderCreated");
        assertThat(outboxRows.get(0).getAggregateType()).isEqualTo("Order");
        assertThat(outboxRows.get(0).getPublishedAt()).isNull();   // outbox-relay ещё не отработал
    }

    @Test
    @DisplayName("BR-014: multi-seller отклоняется без обращения к Catalog")
    void br014_multiSellerRejected() {
        var customerId = CustomerId.of(UUID.randomUUID());
        var productA = ProductId.of(UUID.randomUUID());
        var productB = ProductId.of(UUID.randomUUID());
        var sellerA = SellerId.of(UUID.randomUUID());
        var sellerB = SellerId.of(UUID.randomUUID());

        var useCase = new CreateOrderUseCase(
            customerId,
            List.of(
                new CreateOrderUseCase.Item(productA, sellerA, Quantity.of(1)),
                new CreateOrderUseCase.Item(productB, sellerB, Quantity.of(1))
            ),
            new Address("RU", "Moscow", "Tverskaya 1", "125009", null),
            "idem-key-2",
            "hash-2"
        );

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(useCase))
            .isInstanceOf(MultiSellerNotSupportedException.class);

        // Catalog не должен был вызываться.
        catalog.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathMatching("/api/v1/products/.*")));
    }

    @Test
    @DisplayName("BR-010: повторный вызов с тем же idempotencyKey возвращает прежний orderId")
    void br010_idempotentReplay() {
        var customerId = CustomerId.of(UUID.randomUUID());
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderId = UUID.randomUUID();
        var orderItemId = UUID.randomUUID();
        var now = Instant.parse("2026-04-01T10:00:00Z");

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderId : orderItemId);
        given(dateTimeService.now()).willReturn(now);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson(productJson(productId.value(), "100.00"))));

        var useCase = new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            new Address("RU", "Moscow", "Tverskaya 1", "125009", null),
            "idem-key-3",
            "hash-3"
        );

        var first = useCaseDispatcher.dispatch(useCase);
        var second = useCaseDispatcher.dispatch(useCase);

        assertThat(second.getId()).isEqualTo(first.getId());
        // В БД остался один заказ.
        var countQuery = dsl.fetchCount(
            ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS);
        assertThat(countQuery).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-010: тот же ключ + другой requestHash → IdempotencyKeyConflictException")
    void br010_sameKeyDifferentBodyConflicts() {
        var customerId = CustomerId.of(UUID.randomUUID());
        var sellerId = SellerId.of(UUID.randomUUID());
        var productId = ProductId.of(UUID.randomUUID());
        var orderId = UUID.randomUUID();
        var orderItemId = UUID.randomUUID();
        var now = Instant.parse("2026-04-01T10:00:00Z");

        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderId : orderItemId);
        given(dateTimeService.now()).willReturn(now);

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson(productJson(productId.value(), "100.00"))));

        var address = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);
        var first = new CreateOrderUseCase(customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            address, "idem-key-4", "hash-A");
        var conflicting = new CreateOrderUseCase(customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(2))),
            address, "idem-key-4", "hash-B");

        useCaseDispatcher.dispatch(first);

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(conflicting))
            .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    private String productJson(UUID id, String price) {
        return """
            { "id": "%s", "price": "%s", "currency": "RUB" }
            """.formatted(id, price);
    }
}
