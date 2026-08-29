package ru.remodov.catalog.usecase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.math.BigDecimal;
import java.time.Instant;
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
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;
import ru.remodov.catalog.testsupport.ProductTestObjectGenerator;
import ru.remodov.catalog.testsupport.TestHttpHeaders;

class ChangeProductPriceIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    private final UUID sellerId = UUID.randomUUID();
    private final UUID otherSellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T11:00:00Z"));
    }

    private UUID givenProduct(UUID owner, String price) {
        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(owner).withStatus(ProductStatus.PUBLISHED)
            .withPrice(new BigDecimal(price)).generate();
        databasePreparer.createProduct(product).prepare();
        return productId;
    }

    private <T> org.springframework.http.ResponseEntity<T> changePrice(UUID productId, UUID asSeller,
                                                                      String body, Class<T> type) {
        var headers = TestHttpHeaders.withSellerToken(asSeller);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return restTemplate.exchange(URL + "/" + productId + "/price", HttpMethod.PATCH,
            new HttpEntity<>(body, headers), type);
    }

    @Test
    @DisplayName("владелец меняет цену: 200, новая цена в ответе и в базе")
    void changePrice_whenOwner_returns200AndUpdatesDatabase() {
        var productId = givenProduct(sellerId, "89990.00");

        var response = changePrice(productId, sellerId, "{\"price\":79990.00}", ProductDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("79990.00");

        var priceInDb = databasePreparer.dsl().select(PRODUCTS.PRICE).from(PRODUCTS)
            .where(PRODUCTS.ID.eq(productId)).fetchOne(PRODUCTS.PRICE);
        assertThat(priceInDb).isEqualByComparingTo("79990.00");
    }

    @Test
    @DisplayName("BR-P01: цена не больше нуля — 400 на границе контракта, цена в базе не изменилась")
    void changePrice_whenNotPositive_returns400() {
        var productId = givenProduct(sellerId, "89990.00");

        var response = changePrice(productId, sellerId, "{\"price\":0}", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("code", "VALIDATION_ERROR");

        var priceInDb = databasePreparer.dsl().select(PRODUCTS.PRICE).from(PRODUCTS)
            .where(PRODUCTS.ID.eq(productId)).fetchOne(PRODUCTS.PRICE);
        assertThat(priceInDb).isEqualByComparingTo("89990.00");
    }

    @Test
    @DisplayName("BR-P04: чужой продукт — 404 OWN_PRODUCT_REQUIRED, а не 403")
    void changePrice_whenForeignProduct_returns404() {
        var productId = givenProduct(otherSellerId, "89990.00");

        var response = changePrice(productId, sellerId, "{\"price\":79990.00}", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "OWN_PRODUCT_REQUIRED");

        var priceInDb = databasePreparer.dsl().select(PRODUCTS.PRICE).from(PRODUCTS)
            .where(PRODUCTS.ID.eq(productId)).fetchOne(PRODUCTS.PRICE);
        assertThat(priceInDb).isEqualByComparingTo("89990.00");
    }

    @Test
    @DisplayName("несуществующий продукт — 404 PRODUCT_NOT_FOUND")
    void changePrice_whenUnknownProduct_returns404() {
        var response = changePrice(UUID.randomUUID(), sellerId, "{\"price\":79990.00}", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("админ меняет чужую цену: 200 и запись в журнале действий")
    void changePrice_whenAdmin_returns200AndWritesAudit() {
        var adminId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        var productId = givenProduct(otherSellerId, "89990.00");

        var headers = TestHttpHeaders.withAdminToken(adminId);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var response = restTemplate.exchange(URL + "/" + productId + "/price", HttpMethod.PATCH,
            new HttpEntity<>("{\"price\":1000.00}", headers), ProductDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("1000.00");
        var actions = databasePreparer.dsl()
            .select(ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG.ACTION)
            .from(ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG)
            .fetch(ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG.ACTION);
        assertThat(actions).contains("PRODUCT_PRICE_CHANGED");
    }

    @Test
    @DisplayName("без токена цену не поменять")
    void changePrice_whenAnonymous_isRejected() {
        var productId = givenProduct(sellerId, "89990.00");

        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var response = restTemplate.exchange(URL + "/" + productId + "/price", HttpMethod.PATCH,
            new HttpEntity<>("{\"price\":1.00}", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
