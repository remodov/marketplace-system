package ru.remodov.catalog.mapper;

import org.mapstruct.Mapper;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.generated.api.model.ProductDto;

@Mapper(componentModel = "spring")
public interface ProductJsonBeanMapper {

    ProductDto toDto(Product product);

    ru.remodov.catalog.generated.api.model.ProductStatus toApiStatus(Product.Status status);

    Product.Status toDomainStatus(ru.remodov.catalog.generated.api.model.ProductStatus apiStatus);
}
