package ru.vikulinva.catalogstarter.product;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Товар не найден: " + id);
    }
}
