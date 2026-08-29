package ru.remodov.catalog.usecase.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.ProductNotFoundException;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class GetProductQueryHandler implements UseCaseHandler<GetProductQuery, ProductDto> {

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;

    @Override
    public Class<GetProductQuery> useCaseType() { return GetProductQuery.class; }

    @Override
    @Transactional(readOnly = true)
    public ProductDto handle(GetProductQuery q) {
        Product product = repo.findById(q.productId().value(), ProductRepository.SelectMode.NO_LOCK)
            .orElseThrow(() -> new ProductNotFoundException(q.productId().value()));

        boolean isOwner = q.requesterSellerIdOrNull() != null
            && q.requesterSellerIdOrNull().value().equals(product.sellerId());
        boolean canSeeAnyStatus = q.isAdmin() || isOwner;

        if (!canSeeAnyStatus && product.status() != Product.Status.PUBLISHED) {
            throw new ProductNotFoundException(q.productId().value());
        }
        return mapper.toDto(product);
    }
}
