package ru.remodov.catalog.usecase.product;

import java.math.BigDecimal;
import java.util.Objects;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record ChangeProductPriceUseCase(
    ProductId productId,
    SellerId requesterSellerId,
    boolean isAdmin,
    BigDecimal newPrice
) implements UseCaseCommand<ProductDto> {
    public ChangeProductPriceUseCase {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        Objects.requireNonNull(newPrice, "newPrice");
    }
}
