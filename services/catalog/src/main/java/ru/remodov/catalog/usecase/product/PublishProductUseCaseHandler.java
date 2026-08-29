package ru.remodov.catalog.usecase.product;

import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.audit.AuditLogger;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.InvalidStateTransitionException;
import ru.remodov.catalog.exception.OwnProductRequiredException;
import ru.remodov.catalog.exception.ProductNotFoundException;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class PublishProductUseCaseHandler implements UseCaseHandler<PublishProductUseCase, ProductDto> {

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;
    private final DateTimeService dateTimeService;
    private final AuditLogger auditLogger;

    @Override
    public Class<PublishProductUseCase> useCaseType() { return PublishProductUseCase.class; }

    @Override
    @Transactional
    public ProductDto handle(PublishProductUseCase uc) {
        Product product = repo.findById(uc.productId().value(), ProductRepository.SelectMode.FOR_UPDATE)
            .orElseThrow(() -> new ProductNotFoundException(uc.productId().value()));

        if (!uc.isAdmin() && !product.sellerId().equals(uc.requesterSellerId().value())) {
            throw new OwnProductRequiredException(uc.productId().value());
        }

        Product.Status current = product.status();
        if (current != Product.Status.DRAFT && current != Product.Status.HIDDEN) {
            throw new InvalidStateTransitionException(toDb(current), toDb(Product.Status.PUBLISHED));
        }

        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
        repo.updateStatus(product.id(), Product.Status.PUBLISHED, now);

        Product updated = new Product(
            product.id(), product.title(), product.description(),
            product.price(), product.currency(), product.sellerId(),
            Product.Status.PUBLISHED, product.createdAt(), now
        );

        if (uc.isAdmin()) {
            auditLogger.recordAdminAction(
                uc.requesterSellerId(),
                AuditLogger.ACTION_PRODUCT_PUBLISHED,
                product.id(),
                Map.of("from", current.toString(), "to", Product.Status.PUBLISHED.toString(),
                       "ownerSellerId", product.sellerId().toString())
            );
        }

        return mapper.toDto(updated);
    }

    private ru.remodov.catalog.generated.enums.ProductStatus toDb(Product.Status s) {
        return switch (s) {
            case DRAFT -> ru.remodov.catalog.generated.enums.ProductStatus.DRAFT;
            case PUBLISHED -> ru.remodov.catalog.generated.enums.ProductStatus.PUBLISHED;
            case HIDDEN -> ru.remodov.catalog.generated.enums.ProductStatus.HIDDEN;
        };
    }
}
