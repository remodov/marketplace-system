package ru.remodov.catalog.usecase.product;

import java.util.Objects;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.domain.ProductSortField;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.api.model.ProductPageDto;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

public record ListMyProductsQuery(
    SellerId requesterSellerId,
    Product.Status statusFilter,
    int page,
    int size,
    ProductSortField sort
) implements UseCaseQuery<ProductPageDto> {

    public ListMyProductsQuery {
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        Objects.requireNonNull(sort, "sort");
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be in (0, 100]");
        }
    }
}
