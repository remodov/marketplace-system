package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) implements ValueObject {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId.value must not be null");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }
}
