package ru.remodov.catalog.testsupport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

public class ProductTestObjectGenerator {

    private UUID id = UUID.randomUUID();
    private String title = "Test product " + UUID.randomUUID().toString().substring(0, 8);
    private String description = "Description";
    private BigDecimal price = new BigDecimal("100.00");
    private String currency = "RUB";
    private UUID sellerId = UUID.randomUUID();
    private ProductStatus status = ProductStatus.DRAFT;
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    private OffsetDateTime updatedAt = createdAt;

    public ProductTestObjectGenerator withId(UUID id) { this.id = id; return this; }
    public ProductTestObjectGenerator withTitle(String t) { this.title = t; return this; }
    public ProductTestObjectGenerator withPrice(BigDecimal p) { this.price = p; return this; }
    public ProductTestObjectGenerator withCurrency(String c) { this.currency = c; return this; }
    public ProductTestObjectGenerator withSellerId(UUID s) { this.sellerId = s; return this; }
    public ProductTestObjectGenerator withStatus(ProductStatus s) { this.status = s; return this; }
    public ProductTestObjectGenerator withCreatedAt(OffsetDateTime t) { this.createdAt = t; return this; }
    public ProductTestObjectGenerator withUpdatedAt(OffsetDateTime t) { this.updatedAt = t; return this; }

    public ProductsPojo generate() {
        var p = new ProductsPojo();
        p.setId(id);
        p.setTitle(title);
        p.setDescription(description);
        p.setPrice(price);
        p.setCurrency(currency);
        p.setSellerId(sellerId);
        p.setStatus(status);
        p.setCreatedAt(createdAt);
        p.setUpdatedAt(updatedAt);
        return p;
    }
}
