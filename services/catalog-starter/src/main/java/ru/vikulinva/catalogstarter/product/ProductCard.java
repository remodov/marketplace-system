package ru.vikulinva.catalogstarter.product;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductCard(UUID id, String title, BigDecimal price, int stock, int reserved, int available)
    implements Serializable {

    public static ProductCard of(Product product) {
        return new ProductCard(product.getId(), product.getTitle(), product.getPrice(),
            product.getStock(), product.getReserved(), product.available());
    }
}
