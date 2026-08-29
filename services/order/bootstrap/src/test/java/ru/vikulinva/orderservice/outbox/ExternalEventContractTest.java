package ru.vikulinva.orderservice.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

/**
 * Внешнее событие — это контракт с чужими сервисами, а не дамп внутренних типов.
 * Тест смотрит на то, что реально уедет в Kafka: строку payload из outbox.
 */
class ExternalEventContractTest extends PlatformBaseIntegrationTest {

    @Autowired
    private UseCaseDispatcher useCaseDispatcher;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
        given(uuidGenerator.generate()).willAnswer(inv -> UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));
        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "100.00", "currency": "RUB" }
                """.formatted(productId))));
    }

    private JsonNode publishedPayload() throws Exception {
        useCaseDispatcher.dispatch(new CreateOrderUseCase(
            CustomerId.of(customerId),
            List.of(new CreateOrderUseCase.Item(ProductId.of(productId), SellerId.of(sellerId), Quantity.of(2))),
            new Address("RU", "Moscow", "Tverskaya 1", "125009", null),
            "contract-key-" + UUID.randomUUID(),
            "contract-hash"
        ));
        var row = dsl.selectFrom(OUTBOX).fetchOne();
        assertThat(row).as("событие должно лежать в outbox").isNotNull();
        return objectMapper.readTree(row.getPayload().data());
    }

    @Test
    @DisplayName("идентификаторы уезжают строкой, а не вложенным объектом")
    void identifiersAreFlat() throws Exception {
        JsonNode payload = publishedPayload();

        assertThat(payload.get("customerId").isTextual())
            .as("customerId должен быть строкой: консьюмер читает его строкой")
            .isTrue();
        assertThat(payload.get("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(payload.get("sellerId").isTextual()).isTrue();
        assertThat(payload.get("sellerId").asText()).isEqualTo(sellerId.toString());
        assertThat(payload.at("/items/0/productId").isTextual()).isTrue();
    }

    @Test
    @DisplayName("составной value object пишется своими полями, без производных геттеров")
    void compositeValueObjectHasNoDerivedFields() throws Exception {
        JsonNode total = publishedPayload().get("total");

        assertThat(total.isObject()).isTrue();
        assertThat(total.get("amount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(total.get("currency").asText()).isEqualTo("RUB");
        assertThat(total.has("zero"))
            .as("isZero() — внутреннее удобство, а не часть контракта")
            .isFalse();
    }

    @Test
    @DisplayName("в событии есть всё, что нужно консьюмеру, и ничего лишнего сверху")
    void payloadCarriesWhatConsumerNeeds() throws Exception {
        JsonNode payload = publishedPayload();

        assertThat(payload.get("aggregateId").asText()).isNotBlank();
        assertThat(payload.get("aggregateType").asText()).isEqualTo("Order");
        assertThat(payload.get("itemsCount").asInt()).isEqualTo(1);
        assertThat(payload.at("/items/0/quantity").asInt()).isEqualTo(2);
    }
}
