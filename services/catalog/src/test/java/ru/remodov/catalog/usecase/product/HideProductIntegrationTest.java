package ru.remodov.catalog.usecase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;
import ru.remodov.catalog.testsupport.ProductTestObjectGenerator;
import ru.remodov.catalog.testsupport.TestHttpHeaders;

class HideProductIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T12:00:00Z"));
    }

    @Test
    @DisplayName("AC-C3 / UC-C3 / BR-C5: PUBLISHED → HIDDEN")
    void hide_whenPublished_returns200AndHidden() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.PUBLISHED).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId + "/hide", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(sellerId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus().name()).isEqualTo("HIDDEN");
        var statusInDb = databasePreparer.dsl().select(PRODUCTS.STATUS).from(PRODUCTS)
            .where(PRODUCTS.ID.eq(productId)).fetchOne(PRODUCTS.STATUS);
        assertThat(statusInDb).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    @DisplayName("AC-C4 / BR-P04: чужой seller пытается скрыть → 404 OWN_PRODUCT_REQUIRED")
    void hide_whenOtherSeller_returns404OwnProductRequired() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.PUBLISHED).generate();
        databasePreparer.createProduct(product).prepare();

        var otherSellerId = UUID.randomUUID();
        var response = restTemplate.exchange(
            URL + "/" + productId + "/hide", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(otherSellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "OWN_PRODUCT_REQUIRED");
    }

    @Test
    @DisplayName("BR-C5: hide DRAFT → 409 INVALID_STATE_TRANSITION")
    void hide_whenDraft_returns409() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId + "/hide", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    @DisplayName("AC-C5 / BR-P05: hide HIDDEN → 409 INVALID_STATE_TRANSITION")
    void hide_whenAlreadyHidden_returns409() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.HIDDEN).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId + "/hide", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(sellerId)),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_STATE_TRANSITION");
    }
}
