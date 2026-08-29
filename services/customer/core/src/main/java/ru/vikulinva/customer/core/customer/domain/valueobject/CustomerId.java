package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) implements ValueObject {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId.value");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public String asString() {
        return value.toString();
    }
}
