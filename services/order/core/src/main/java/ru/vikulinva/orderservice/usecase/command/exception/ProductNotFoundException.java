package ru.vikulinva.orderservice.usecase.command.exception;

import ru.vikulinva.orderservice.domain.valueobject.ProductId;

import java.util.List;

/** Catalog не нашёл один или несколько товаров. */
public final class ProductNotFoundException extends OrderDomainException {

    public ProductNotFoundException(List<ProductId> missing) {
        super("PRODUCT_NOT_FOUND", 404,
            "Products not found: " + missing.stream().map(p -> p.value().toString()).toList());
    }
}
