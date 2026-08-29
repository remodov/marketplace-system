package ru.remodov.catalog.usecase.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.domain.PageView;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.generated.api.model.ProductPageDto;
import ru.remodov.catalog.mapper.ProductJsonBeanMapper;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class ListMyProductsQueryHandler implements UseCaseHandler<ListMyProductsQuery, ProductPageDto> {

    private final ProductRepository repo;
    private final ProductJsonBeanMapper mapper;

    @Override
    public Class<ListMyProductsQuery> useCaseType() { return ListMyProductsQuery.class; }

    @Override
    @Transactional(readOnly = true)
    public ProductPageDto handle(ListMyProductsQuery q) {
        PageView<Product> view = repo.findBySeller(
            q.requesterSellerId().value(),
            q.statusFilter(),
            (q.page() - 1) * q.size(),
            q.size(),
            q.sort()
        );

        var dto = new ProductPageDto();
        dto.setContent(view.content().stream().map(mapper::toDto).toList());
        dto.setPage(view.page());
        dto.setSize(view.size());
        dto.setTotalElements(view.totalElements());
        dto.setTotalPages(view.totalPages());
        return dto;
    }
}
