package ru.vikulinva.orderservice.adapter.out.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Минимальный DTO ответа Catalog Service на {@code GET /api/v1/products/{id}}.
 * Поля camelCase, цена строкой (см. REST API Style Guide).
 */
public record CatalogProductResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("currency") String currency
) {
}
