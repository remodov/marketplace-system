package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.UUID;

public record OrderItemId(UUID value) implements ValueObject {

    public OrderItemId {
        Objects.requireNonNull(value, "OrderItemId.value must not be null");
    }

    public static OrderItemId of(UUID value) {
        return new OrderItemId(value);
    }
}
