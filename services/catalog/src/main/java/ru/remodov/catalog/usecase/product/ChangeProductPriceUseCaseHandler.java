package ru.remodov.catalog.usecase.product;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.audit.AuditLogger;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.InvalidPriceException;
import ru.remodov.catalog.exception.OwnProductRequiredException;
import ru.remodov.catalog.exception.ProductNotFoundException;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class ChangeProductPriceUseCaseHandler
    implements UseCaseHandler<ChangeProductPriceUseCase, ProductDto> {

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;
    private final DateTimeService dateTimeService;
    private final AuditLogger auditLogger;

    @Override
    public Class<ChangeProductPriceUseCase> useCaseType() { return ChangeProductPriceUseCase.class; }

    @Override
    @Transactional
    public ProductDto handle(ChangeProductPriceUseCase uc) {
        if (uc.newPrice().signum() <= 0) {
            throw new InvalidPriceException("Цена должна быть больше нуля, а не " + uc.newPrice().toPlainString());
        }

        Product product = repo.findById(uc.productId().value(), ProductRepository.SelectMode.FOR_UPDATE)
            .orElseThrow(() -> new ProductNotFoundException(uc.productId().value()));

        if (!uc.isAdmin() && !product.sellerId().equals(uc.requesterSellerId().value())) {
            throw new OwnProductRequiredException(uc.productId().value());
        }

        BigDecimal previous = product.price();
        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
        repo.updatePrice(product.id(), uc.newPrice(), now);

        Product updated = new Product(
            product.id(), product.title(), product.description(),
            uc.newPrice(), product.currency(), product.sellerId(),
            product.status(), product.createdAt(), now
        );

        if (uc.isAdmin()) {
            auditLogger.recordAdminAction(
                uc.requesterSellerId(),
                AuditLogger.ACTION_PRODUCT_PRICE_CHANGED,
                product.id(),
                Map.of("from", previous.toPlainString(), "to", uc.newPrice().toPlainString(),
                       "ownerSellerId", product.sellerId().toString())
            );
        }

        return mapper.toDto(updated);
    }
}
