package ru.remodov.catalog.usecase.product;

import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.core.service.UuidGenerator;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.InvalidCurrencyException;
import ru.remodov.catalog.exception.InvalidPriceException;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class CreateProductUseCaseHandler implements UseCaseHandler<CreateProductUseCase, ProductDto> {

    private static final String SUPPORTED_CURRENCY = "RUB";

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;
    private final DateTimeService dateTimeService;
    private final UuidGenerator uuidGenerator;

    @Override
    public Class<CreateProductUseCase> useCaseType() { return CreateProductUseCase.class; }

    @Override
    @Transactional
    public ProductDto handle(CreateProductUseCase uc) {
        validate(uc);
        var now = dateTimeService.now().atOffset(ZoneOffset.UTC);
        var product = new Product(
            uuidGenerator.generate(),
            uc.title(),
            uc.description(),
            uc.price(),
            uc.currency(),
            uc.sellerId().value(),
            Product.Status.DRAFT,
            now,
            now
        );
        repo.insert(product);
        return mapper.toDto(product);
    }

    private void validate(CreateProductUseCase uc) {
        if (uc.title().isBlank()) {
            throw new IllegalArgumentException("title must be non-empty");
        }
        if (uc.price().signum() <= 0) {
            throw new InvalidPriceException("price must be > 0");
        }
        if (!SUPPORTED_CURRENCY.equals(uc.currency())) {
            throw new InvalidCurrencyException(uc.currency());
        }
    }
}
