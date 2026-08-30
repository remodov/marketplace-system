package ru.remodov.backoffice.catalog;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.remodov.backoffice.catalog.dto.ProductView;
import ru.remodov.backoffice.catalog.generated.api.model.ProductDto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogMapper {

    ProductView toView(ProductDto dto);

    default String toCurrencyCode(ProductDto.CurrencyEnum currency) {
        return currency == null ? null : currency.getValue();
    }
}
