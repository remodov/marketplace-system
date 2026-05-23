package ru.remodov.backoffice.moderation.exception;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ProductNotFoundException extends BackofficeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId, Throwable cause) {
        super("Product not found: " + productId, cause);
        this.productId = productId;
    }

    @Override
    public String code() {
        return "PRODUCT_NOT_FOUND";
    }

    @Override
    public int status() {
        return 404;
    }
}
