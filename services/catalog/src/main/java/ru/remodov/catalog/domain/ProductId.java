package ru.remodov.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {
    public ProductId {
        Objects.requireNonNull(value, "ProductId.value");
    }
    public static ProductId of(UUID value) { return new ProductId(value); }
}
