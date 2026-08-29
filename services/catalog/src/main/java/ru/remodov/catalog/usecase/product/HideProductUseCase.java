package ru.remodov.catalog.usecase.product;

import java.util.Objects;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record HideProductUseCase(
    ProductId productId,
    SellerId requesterSellerId,
    boolean isAdmin
) implements UseCaseCommand<ProductDto> {
    public HideProductUseCase {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
    }
}
