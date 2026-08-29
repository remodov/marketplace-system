package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

public record Quantity(int value) implements ValueObject {

    private static final int MIN = 1;
    private static final int MAX = 999;

    public Quantity {
        if (value < MIN) {
            throw new IllegalArgumentException("Quantity must be >= " + MIN + ", got " + value);
        }
        if (value > MAX) {
            throw new IllegalArgumentException("Quantity must be <= " + MAX + ", got " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }
}
