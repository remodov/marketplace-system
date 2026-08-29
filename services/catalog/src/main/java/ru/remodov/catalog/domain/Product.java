package ru.remodov.catalog.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Product(
    UUID id,
    String title,
    String description,
    BigDecimal price,
    String currency,
    UUID sellerId,
    Status status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public enum Status { DRAFT, PUBLISHED, HIDDEN }
}
