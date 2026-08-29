package ru.remodov.catalog.usecase.product;

import static org.assertj.core.api.Assertions.assertThat;

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

class GetProductIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
    }

    @Test
    @DisplayName("AC-C8 / UC-C2 / BR-C6: GET PUBLISHED → 200 (анонимно, без токена)")
    void get_whenPublished_returns200Anonymous() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.PUBLISHED).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.anonymous()),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(productId);
        assertThat(response.getBody().getStatus().name()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("AC-C8 / BR-C6: GET DRAFT → 404 PRODUCT_NOT_FOUND (даже владельцу через публичный endpoint)")
    void get_whenDraft_returns404() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.anonymous()),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("BR-C6: GET HIDDEN → 404 PRODUCT_NOT_FOUND")
    void get_whenHidden_returns404() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.HIDDEN).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.anonymous()),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-C9: service-account читает PUBLISHED → 200 с ценой")
    void get_whenServiceAccountReadsPublished_returns200WithPrice() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.PUBLISHED).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withServiceAccountToken()),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(productId);
        assertThat(response.getBody().getPrice()).isEqualByComparingTo(product.getPrice());
    }

    @Test
    @DisplayName("AC-C9: service-account читает DRAFT → 404 PRODUCT_NOT_FOUND")
    void get_whenServiceAccountReadsDraft_returns404() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withServiceAccountToken()),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("Phase 4: владелец читает свой DRAFT → 200")
    void get_whenOwnerReadsOwnDraft_returns200() {
        var sellerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(sellerId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(productId);
        assertThat(response.getBody().getStatus().name()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Phase 4: владелец читает свой HIDDEN → 200")
    void get_whenOwnerReadsOwnHidden_returns200() {
        var sellerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(sellerId).withStatus(ProductStatus.HIDDEN).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(sellerId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(productId);
        assertThat(response.getBody().getStatus().name()).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("Phase 4: admin читает чужой DRAFT → 200")
    void get_whenAdminReadsOtherDraft_returns200() {
        var adminId = UUID.randomUUID();
        var ownerSellerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(ownerSellerId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.withAdminToken(adminId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(productId);
        assertThat(response.getBody().getStatus().name()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Phase 4: анонимный читает DRAFT → 404")
    void get_whenAnonymousReadsDraft_returns404() {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId, HttpMethod.GET,
            new HttpEntity<>(TestHttpHeaders.anonymous()),
            ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PRODUCT_NOT_FOUND");
    }
}
