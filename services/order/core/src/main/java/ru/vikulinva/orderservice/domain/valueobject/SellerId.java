package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record SellerId(UUID value) implements ValueObject {

    public SellerId {
        Objects.requireNonNull(value, "SellerId.value must not be null");
    }

    public static SellerId of(UUID value) {
        return new SellerId(value);
    }
}
