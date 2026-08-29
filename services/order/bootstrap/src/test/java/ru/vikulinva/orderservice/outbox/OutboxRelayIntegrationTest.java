package ru.vikulinva.orderservice.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.vikulinva.orderservice.adapter.out.postgres.outbox.OutboxRelay;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Integration test для outbox-relay. Используем @MockitoBean
 * {@link ExternalEventPublisher}, чтобы verify-нуть вызовы и эмулировать ошибки.
 */
class OutboxRelayIntegrationTest extends PlatformBaseIntegrationTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private org.jooq.DSLContext dsl;

    @MockitoBean
    private ExternalEventPublisher externalEventPublisher;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("happy path: relay публикует pending-события и помечает published_at")
    void relay_publishesAndMarks() {
        createDraftOrder("200.00", 1);
        long unpublishedBefore = countUnpublished();
        assertThat(unpublishedBefore).isEqualTo(1);

        int processed = outboxRelay.relayBatch();

        assertThat(processed).isEqualTo(1);
        verify(externalEventPublisher, times(1))
            .publish(any(UUID.class), eqStr("Order"), any(UUID.class),
                eqStr("OrderCreated"), anyInt(), anyString(), any(Instant.class));
        assertThat(countUnpublished()).isZero();
    }

    @Test
    @DisplayName("при ошибке publish — published_at не обновляется, событие остаётся pending")
    void relay_publishFails_keepsPending() {
        createDraftOrder("200.00", 2);
        doThrow(new RuntimeException("kafka down"))
            .when(externalEventPublisher)
            .publish(any(), anyString(), any(), anyString(), anyInt(), anyString(), any());

        assertThatThrownBy(() -> outboxRelay.relayBatch())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("kafka down");

        assertThat(countUnpublished()).isEqualTo(1);
    }

    @Test
    @DisplayName("повторный вызов после успеха — нечего публиковать")
    void relay_idempotentTick() {
        createDraftOrder("200.00", 3);
        doNothing().when(externalEventPublisher)
            .publish(any(), anyString(), any(), anyString(), anyInt(), anyString(), any());

        assertThat(outboxRelay.relayBatch()).isEqualTo(1);
        assertThat(outboxRelay.relayBatch()).isZero();
    }

    private long countUnpublished() {
        return dsl.fetchCount(dsl.selectOne().from(OUTBOX).where(OUTBOX.PUBLISHED_AT.isNull()));
    }

    private void createDraftOrder(String price, int idemSuffix) {
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

        useCaseDispatcher.dispatch(new CreateOrderUseCase(
            customerId,
            List.of(new CreateOrderUseCase.Item(productId, sellerId, Quantity.of(1))),
            ADDRESS,
            "idem-relay-" + idemSuffix,
            "hash-relay-" + idemSuffix
        ));
    }

    private static String eqStr(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }
}
