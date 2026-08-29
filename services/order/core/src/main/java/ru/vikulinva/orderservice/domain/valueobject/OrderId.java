package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) implements ValueObject {

    public OrderId {
        Objects.requireNonNull(value, "OrderId.value must not be null");
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }
}
