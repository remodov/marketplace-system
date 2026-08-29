package ru.remodov.backoffice.catalog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductView(
    UUID id,
    String title,
    String description,
    BigDecimal price,
    String currency,
    UUID sellerId,
    ProductStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
