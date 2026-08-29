package ru.remodov.catalog.usecase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.generated.api.model.ProductStatus;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;
import ru.remodov.catalog.testsupport.TestHttpHeaders;

class CreateProductIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
    }

    @Test
    @DisplayName("AC-C1 / UC-C1 happy path: seller creates DRAFT product with server-generated UUID")
    void create_whenValidPayload_returns201AndDraft() {
        var generatedId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(generatedId);
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T10:00:00Z"));

        var body = Map.of(
            "title", "iPhone 15 Pro",
            "description", "Б/у, идеал",
            "price", new BigDecimal("89990.00"),
            "currency", "RUB"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/products/" + generatedId);
        ProductDto dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(generatedId);
        assertThat(dto.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(dto.getSellerId()).isEqualTo(sellerId);

        var rowCount = databasePreparer.dsl().selectCount().from(PRODUCTS).fetchOne(0, Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-C6 / BR-C1: price <= 0 → 400")
    void create_whenPriceZero_returns400InvalidPrice() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.now());

        var body = Map.of(
            "title", "T", "description", "",
            "price", new BigDecimal("0"), "currency", "RUB"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code"))
            .isIn("INVALID_PRICE", "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("AC-C7 / BR-C2: currency != RUB → 400 INVALID_CURRENCY")
    void create_whenCurrencyUSD_returns400InvalidCurrency() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.now());

        var body = Map.of(
            "title", "T", "description", "",
            "price", new BigDecimal("100"), "currency", "USD"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code"))
            .isIn("INVALID_CURRENCY", "MALFORMED_REQUEST", "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("BR-P03: id из тела игнорируется, сервер генерирует свой UUID")
    void create_whenClientSendsId_serverIgnoresIt() {
        var clientSentId = UUID.randomUUID();
        var serverGeneratedId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(serverGeneratedId);
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T10:00:00Z"));

        var body = Map.of(
            "id", clientSentId.toString(),
            "title", "T", "description", "",
            "price", new BigDecimal("100"), "currency", "RUB"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(serverGeneratedId);
        assertThat(response.getBody().getId()).isNotEqualTo(clientSentId);
    }

    @Test
    @DisplayName("BR-P01: отрицательная цена → 400")
    void create_whenPriceNegative_returns400InvalidPrice() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.now());

        var body = Map.of(
            "title", "T", "description", "",
            "price", new BigDecimal("-10.00"), "currency", "RUB"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code"))
            .isIn("INVALID_PRICE", "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("BR-P01: price=null → 400")
    void create_whenPriceNull_returns400() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.now());

        var body = new HashMap<String, Object>();
        body.put("title", "T");
        body.put("description", "");
        body.put("price", null);
        body.put("currency", "RUB");

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("BR-P02: currency=null → 400")
    void create_whenCurrencyNull_returns400() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.now());

        var body = new HashMap<String, Object>();
        body.put("title", "T");
        body.put("description", "");
        body.put("price", new BigDecimal("100"));
        body.put("currency", null);

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("AUTH-9: customer-роль на POST /products → 403")
    void create_whenCustomerRole_returns403() {
        var body = Map.of(
            "title", "T", "description", "",
            "price", new BigDecimal("100"), "currency", "RUB"
        );

        var response = restTemplate.exchange(
            URL, HttpMethod.POST,
            new HttpEntity<>(body, TestHttpHeaders.withCustomerToken(UUID.randomUUID())),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
