package ru.remodov.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record SellerId(UUID value) {
    public SellerId {
        Objects.requireNonNull(value, "SellerId.value");
    }
    public static SellerId of(UUID value) { return new SellerId(value); }
}
