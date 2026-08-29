package ru.vikulinva.orderservice.adapter.out.catalog;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.retry.RetryRegistry;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.exception.CatalogUnavailableException;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;

/**
 * Поведение заказа, когда сосед отвечает медленно, срывается или лежит.
 * Каталог здесь — WireMock: он умеет и уронить соединение, и держать ответ.
 */
class CatalogResilienceIntegrationTest extends PlatformBaseIntegrationTest {

    private static final String PRODUCTS = "/api/v1/products/.*";
    private static final String RETRY_SCENARIO = "catalog-retry";

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private RetryRegistry retryRegistry;

    private final CustomerId customerId = CustomerId.of(UUID.randomUUID());
    private final SellerId sellerId = SellerId.of(UUID.randomUUID());
    private final ProductId productId = ProductId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
        AtomicInteger uuidCallCount = new AtomicInteger();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderId : itemId);
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));
    }

    private CreateOrderUseCase order(String idempotencyKey) {
        return new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            new Address("RU", "Moscow", "Tverskaya 1", "125009", null),
            idempotencyKey,
            "hash-" + idempotencyKey
        );
    }

    private String productJson() {
        return """
            { "id": "%s", "price": "100.00", "currency": "RUB" }
            """.formatted(productId.value());
    }

    @Test
    @DisplayName("один зависший ответ переживается повтором: заказ создан, повтор виден в метриках")
    void singleFailure_isSurvivedByRetry() {
        catalog.stubFor(get(urlPathMatching(PRODUCTS)).inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(productJson()).withFixedDelay(2500))
            .willSetStateTo("recovered"));
        catalog.stubFor(get(urlPathMatching(PRODUCTS)).inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs("recovered")
            .willReturn(okJson(productJson())));

        var result = useCaseDispatcher.dispatch(order("retry-1"));

        assertThat(result.getId()).isNotNull();
        assertThat(dsl.fetchCount(ORDERS)).isEqualTo(1);
        catalog.verify(2, getRequestedFor(urlPathMatching(PRODUCTS)));
        assertThat(retryRegistry.retry("catalog").getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt())
            .as("вызов, который удался только со второго захода")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("каталог лежит: заказ не создаётся, ошибка говорит о недоступности соседа")
    void catalogDown_failsWithoutCreatingOrder() {
        catalog.stubFor(get(urlPathMatching(PRODUCTS))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        // Сорванное соединение JDK-клиент повторяет и сам, поэтому здесь важен
        // не счётчик запросов, а тип ошибки и пустая база.

        assertThatThrownBy(() -> useCaseDispatcher.dispatch(order("down-1")))
            .isInstanceOf(CatalogUnavailableException.class);

        assertThat(dsl.fetchCount(ORDERS)).isZero();
    }

    @Test
    @DisplayName("каталог отвечает слишком долго: ждём не дольше таймаута, а не до победного")
    void slowCatalog_isCutOffByTimeout() {
        catalog.stubFor(get(urlPathMatching(PRODUCTS))
            .willReturn(okJson(productJson()).withFixedDelay(4000)));

        Instant startedAt = Instant.now();
        assertThatThrownBy(() -> useCaseDispatcher.dispatch(order("slow-1")))
            .isInstanceOf(CatalogUnavailableException.class);
        Duration spent = Duration.between(startedAt, Instant.now());

        assertThat(spent).as("оба захода должны упереться в таймаут чтения").isLessThan(Duration.ofSeconds(4));
        assertThat(dsl.fetchCount(ORDERS)).isZero();
    }
}
