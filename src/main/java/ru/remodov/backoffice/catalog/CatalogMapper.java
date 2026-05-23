package ru.remodov.backoffice.catalog;

import org.springframework.stereotype.Component;
import ru.remodov.backoffice.catalog.dto.ProductStatus;
import ru.remodov.backoffice.catalog.dto.ProductView;
import ru.remodov.backoffice.catalog.generated.api.model.ProductDto;

@Component
public class CatalogMapper {

    public ProductView toView(ProductDto dto) {
        return new ProductView(
            dto.getId(),
            dto.getTitle(),
            dto.getDescription(),
            dto.getPrice(),
            dto.getCurrency() != null ? dto.getCurrency().getValue() : null,
            dto.getSellerId(),
            toStatus(dto.getStatus()),
            dto.getCreatedAt(),
            dto.getUpdatedAt()
        );
    }

    private ProductStatus toStatus(ru.remodov.backoffice.catalog.generated.api.model.ProductStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> ProductStatus.DRAFT;
            case PUBLISHED -> ProductStatus.PUBLISHED;
            case HIDDEN -> ProductStatus.HIDDEN;
        };
    }
}
