package ru.remodov.backoffice.moderation.exception;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ProductAlreadyHiddenException extends BackofficeException {

    private final UUID productId;

    public ProductAlreadyHiddenException(UUID productId, Throwable cause) {
        super("Product already hidden: " + productId, cause);
        this.productId = productId;
    }

    @Override
    public String code() {
        return "PRODUCT_ALREADY_HIDDEN";
    }

    @Override
    public int status() {
        return 409;
    }
}
