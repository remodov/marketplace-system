package ru.remodov.catalog.usecase.image;

import java.util.Objects;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.image.ProductImageService;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record RequestImageUploadUseCase(
    ProductId productId,
    SellerId requesterSellerId,
    boolean isAdmin,
    String contentType
) implements UseCaseCommand<ProductImageService.PresignedUpload> {
    public RequestImageUploadUseCase {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(requesterSellerId, "requesterSellerId");
        Objects.requireNonNull(contentType, "contentType");
    }
}
