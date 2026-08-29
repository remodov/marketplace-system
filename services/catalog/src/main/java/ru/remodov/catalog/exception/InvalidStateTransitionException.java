package ru.remodov.catalog.exception;

import ru.remodov.catalog.generated.enums.ProductStatus;

public class InvalidStateTransitionException extends RuntimeException {
    private final ProductStatus from;
    private final ProductStatus to;

    public InvalidStateTransitionException(ProductStatus from, ProductStatus to) {
        super("Invalid product status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public ProductStatus from() { return from; }
    public ProductStatus to() { return to; }
}
