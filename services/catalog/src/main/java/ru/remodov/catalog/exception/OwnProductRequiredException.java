package ru.remodov.catalog.exception;

import java.util.UUID;

public class OwnProductRequiredException extends RuntimeException {
    private final UUID productId;

    public OwnProductRequiredException(UUID productId) {
        super("Caller does not own product: " + productId);
        this.productId = productId;
    }

    public UUID productId() { return productId; }
}
