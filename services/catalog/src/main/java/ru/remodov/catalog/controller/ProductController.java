package ru.remodov.catalog.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.remodov.catalog.api.AuthenticatedSeller;
import ru.remodov.catalog.domain.ProductId;
import ru.remodov.catalog.domain.ProductSortField;
import ru.remodov.catalog.generated.api.ProductsApi;
import ru.remodov.catalog.generated.api.model.ChangePriceRequest;
import ru.remodov.catalog.generated.api.model.CreateProductRequest;
import ru.remodov.catalog.generated.api.model.ProductDto;
import ru.remodov.catalog.generated.api.model.ProductPageDto;
import ru.remodov.catalog.generated.api.model.ProductStatus;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.usecase.product.ChangeProductPriceUseCase;
import ru.remodov.catalog.usecase.product.CreateProductUseCase;
import ru.remodov.catalog.usecase.product.GetProductQuery;
import ru.remodov.catalog.usecase.product.HideProductUseCase;
import ru.remodov.catalog.usecase.product.ListMyProductsQuery;
import ru.remodov.catalog.usecase.product.PublishProductUseCase;
import ru.vikulinva.usecase.UseCaseDispatcher;

@RestController
@RequiredArgsConstructor
public class ProductController implements ProductsApi {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;

    private final UseCaseDispatcher dispatcher;
    private final AuthenticatedSeller authenticatedSeller;
    private final ProductJsonBeanMapper mapper;

    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductDto> createProduct(CreateProductRequest req) {
        var sellerId = authenticatedSeller.currentSellerId();
        var useCase = new CreateProductUseCase(
            sellerId, req.getTitle(), req.getDescription(),
            req.getPrice(), req.getCurrency().getValue()
        );
        ProductDto product = dispatcher.dispatch(useCase);
        return ResponseEntity
            .created(URI.create("/api/v1/products/" + product.getId()))
            .body(product);
    }

    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<ProductDto> getProduct(UUID productId) {
        var requester = authenticatedSeller.tryCurrentSellerId().orElse(null);
        boolean isAdmin = authenticatedSeller.isAdmin();
        return ResponseEntity.ok(
            dispatcher.dispatch(new GetProductQuery(ProductId.of(productId), requester, isAdmin))
        );
    }

    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductDto> publishProduct(UUID productId) {
        var sellerId = authenticatedSeller.currentSellerId();
        boolean isAdmin = authenticatedSeller.isAdmin();
        return ResponseEntity.ok(
            dispatcher.dispatch(new PublishProductUseCase(ProductId.of(productId), sellerId, isAdmin))
        );
    }

    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductDto> changeProductPrice(UUID productId, ChangePriceRequest req) {
        var sellerId = authenticatedSeller.currentSellerId();
        boolean isAdmin = authenticatedSeller.isAdmin();
        return ResponseEntity.ok(dispatcher.dispatch(
            new ChangeProductPriceUseCase(ProductId.of(productId), sellerId, isAdmin, req.getPrice())
        ));
    }

    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductDto> hideProduct(UUID productId) {
        var sellerId = authenticatedSeller.currentSellerId();
        boolean isAdmin = authenticatedSeller.isAdmin();
        return ResponseEntity.ok(
            dispatcher.dispatch(new HideProductUseCase(ProductId.of(productId), sellerId, isAdmin))
        );
    }

    @Override
    @PreAuthorize("hasRole('seller') or hasRole('admin')")
    public ResponseEntity<ProductPageDto> listMyProducts(
        ProductStatus status,
        Integer page,
        Integer size,
        String sort
    ) {
        var sellerId = authenticatedSeller.currentSellerId();
        var domainStatus = status == null ? null : mapper.toDomainStatus(status);
        return ResponseEntity.ok(
            dispatcher.dispatch(new ListMyProductsQuery(
                sellerId,
                domainStatus,
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size,
                ProductSortField.parse(sort)
            ))
        );
    }
}
