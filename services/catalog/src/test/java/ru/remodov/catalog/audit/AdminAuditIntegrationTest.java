package ru.remodov.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.testsupport.CatalogBaseIntegrationTest;
import ru.remodov.catalog.testsupport.ProductTestObjectGenerator;
import ru.remodov.catalog.testsupport.TestHttpHeaders;

class AdminAuditIntegrationTest extends CatalogBaseIntegrationTest {

    private static final String URL = "/api/v1/products";

    private final UUID adminId = UUID.randomUUID();
    private final UUID ownerSellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAuditLog().clearProducts().prepare();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-28T13:00:00Z"));
    }

    @Test
    @DisplayName("AUTH-15: admin публикует чужой продукт → строка в catalog_audit_log")
    void publishByAdmin_writesAuditRow() {
        var auditId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(auditId);

        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(ownerSellerId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId + "/publish", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withAdminToken(adminId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var rows = databasePreparer.dsl()
            .selectFrom(CATALOG_AUDIT_LOG)
            .where(CATALOG_AUDIT_LOG.PRODUCT_ID.eq(productId))
            .fetch();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getActorId()).isEqualTo(adminId);
        assertThat(row.getAction()).isEqualTo("PRODUCT_PUBLISHED");
        assertThat(row.getMetadata().toString())
            .contains("\"from\":\"DRAFT\"")
            .contains("\"to\":\"PUBLISHED\"")
            .contains(ownerSellerId.toString());
    }

    @Test
    @DisplayName("BR-P04 admin path: admin скрывает чужой PUBLISHED → 200 HIDDEN")
    void hideByAdmin_bypassesOwnerCheck() {
        var auditId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(auditId);

        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(ownerSellerId).withStatus(ProductStatus.PUBLISHED).generate();
        databasePreparer.createProduct(product).prepare();

        var response = restTemplate.exchange(
            URL + "/" + productId + "/hide", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withAdminToken(adminId)),
            ProductDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus().name()).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("AUTH-15: seller публикует свой продукт → audit-таблица пустая")
    void publishByOwner_doesNotWriteAudit() {
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());

        var productId = UUID.randomUUID();
        var product = new ProductTestObjectGenerator()
            .withId(productId).withSellerId(ownerSellerId).withStatus(ProductStatus.DRAFT).generate();
        databasePreparer.createProduct(product).prepare();

        restTemplate.exchange(
            URL + "/" + productId + "/publish", HttpMethod.POST,
            new HttpEntity<>(TestHttpHeaders.withSellerToken(ownerSellerId)),
            ProductDto.class
        );

        var count = databasePreparer.dsl().selectCount().from(CATALOG_AUDIT_LOG).fetchOne(0, Integer.class);
        assertThat(count).isZero();
    }
}
