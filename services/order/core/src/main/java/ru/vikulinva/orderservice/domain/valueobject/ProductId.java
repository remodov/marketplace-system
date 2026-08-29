package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) implements ValueObject {

    public ProductId {
        Objects.requireNonNull(value, "ProductId.value must not be null");
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }
}
