package ru.remodov.catalog.repository;

import org.springframework.stereotype.Component;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

@Component
public class ProductDomainRecordMapper {

    public Product toDomain(ProductsPojo pojo) {
        return new Product(
            pojo.getId(),
            pojo.getTitle(),
            pojo.getDescription(),
            pojo.getPrice(),
            pojo.getCurrency(),
            pojo.getSellerId(),
            toDomainStatus(pojo.getStatus()),
            pojo.getCreatedAt(),
            pojo.getUpdatedAt()
        );
    }

    public ProductsPojo fromDomain(Product product) {
        var pojo = new ProductsPojo();
        pojo.setId(product.id());
        pojo.setTitle(product.title());
        pojo.setDescription(product.description());
        pojo.setPrice(product.price());
        pojo.setCurrency(product.currency());
        pojo.setSellerId(product.sellerId());
        pojo.setStatus(toDbStatus(product.status()));
        pojo.setCreatedAt(product.createdAt());
        pojo.setUpdatedAt(product.updatedAt());
        return pojo;
    }

    private Product.Status toDomainStatus(ProductStatus s) {
        return switch (s) {
            case DRAFT -> Product.Status.DRAFT;
            case PUBLISHED -> Product.Status.PUBLISHED;
            case HIDDEN -> Product.Status.HIDDEN;
        };
    }

    private ProductStatus toDbStatus(Product.Status s) {
        return switch (s) {
            case DRAFT -> ProductStatus.DRAFT;
            case PUBLISHED -> ProductStatus.PUBLISHED;
            case HIDDEN -> ProductStatus.HIDDEN;
        };
    }
}
