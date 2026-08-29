package ru.vikulinva.catalogstarter.product;

import java.util.UUID;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(UUID productId, int requested, int available) {
        super("Товара %s не хватает: просят %d, на складе %d".formatted(productId, requested, available));
    }
}
