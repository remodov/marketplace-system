package ru.remodov.catalog.usecase.product;

import java.util.Objects;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

public record GetProductQuery(
    ProductId productId,
    SellerId requesterSellerIdOrNull,
    boolean isAdmin
) implements UseCaseQuery<ProductDto> {

    public GetProductQuery {
        Objects.requireNonNull(productId, "productId");
    }

    public static GetProductQuery anonymous(ProductId productId) {
        return new GetProductQuery(productId, null, false);
    }
}
