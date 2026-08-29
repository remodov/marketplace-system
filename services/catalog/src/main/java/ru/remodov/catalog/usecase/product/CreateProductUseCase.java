package ru.remodov.catalog.usecase.product;

import java.math.BigDecimal;
import java.util.Objects;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record CreateProductUseCase(
    SellerId sellerId,
    String title,
    String description,
    BigDecimal price,
    String currency
) implements UseCaseCommand<ProductDto> {

    public CreateProductUseCase {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
    }
}
