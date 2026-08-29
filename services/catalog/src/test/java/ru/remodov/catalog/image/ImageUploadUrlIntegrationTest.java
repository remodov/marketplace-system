package ru.remodov.catalog.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;
import ru.remodov.catalog.testsupport.ProductTestObjectGenerator;
import ru.remodov.catalog.testsupport.TestHttpHeaders;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Ссылка на загрузку изображения: кому её выдаём и что в ней лежит.
 * Файл через сервис не идёт — сервис только подписывает ссылку.
 */
class ImageUploadUrlIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    private final UUID sellerId = UUID.randomUUID();
    private final UUID otherSellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T11:00:00Z"));
    }

    private UUID givenProduct(UUID owner) {
        var productId = UUID.randomUUID();
        databasePreparer.createProduct(new ProductTestObjectGenerator()
            .withId(productId).withSellerId(owner).withStatus(ProductStatus.PUBLISHED).generate()).prepare();
        return productId;
    }

    private <T> org.springframework.http.ResponseEntity<T> askUploadUrl(UUID productId, UUID asSeller, Class<T> type) {
        HttpHeaders headers = TestHttpHeaders.withSellerToken(asSeller);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(URL + "/" + productId + "/image-upload-url", HttpMethod.POST,
            new HttpEntity<>("{\"contentType\":\"image/jpeg\"}", headers), type);
    }

    @Test
    @DisplayName("владелец получает подписанную ссылку с ключом внутри своего товара")
    @SuppressWarnings("unchecked")
    void ownerGetsPresignedUrl() {
        var productId = givenProduct(sellerId);

        var response = askUploadUrl(productId, sellerId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat((String) body.get("key")).startsWith("products/" + productId + "/");
        String url = (String) body.get("url");
        assertThat(url).contains("marketplace-images");
        assertThat(url)
            .as("ссылка временная: в ней есть подпись и срок жизни")
            .contains("X-Amz-Signature").contains("X-Amz-Expires");
        assertThat((String) body.get("expiresAt")).isNotBlank();
    }

    @Test
    @DisplayName("на чужой товар ссылку не дают — и отвечают 404, а не 403")
    void foreignProductGivesNotFound() {
        var productId = givenProduct(otherSellerId);

        var response = askUploadUrl(productId, sellerId, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "OWN_PRODUCT_REQUIRED");
    }

    @Test
    @DisplayName("несуществующий товар — 404")
    void unknownProductGivesNotFound() {
        var response = askUploadUrl(UUID.randomUUID(), sellerId, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("без токена ссылку не выдают")
    void anonymousIsRejected() {
        var productId = givenProduct(sellerId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.exchange(URL + "/" + productId + "/image-upload-url", HttpMethod.POST,
            new HttpEntity<>("{\"contentType\":\"image/jpeg\"}", headers), Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
