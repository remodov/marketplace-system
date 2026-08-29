package ru.remodov.catalog.repository;

import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.stereotype.Repository;
import ru.remodov.catalog.domain.PageView;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.domain.ProductSortField;
import ru.remodov.catalog.generated.enums.ProductStatus;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

@Repository
@RequiredArgsConstructor
public class JooqProductRepository implements ProductRepository {

    private final DSLContext dsl;
    private final ProductDomainRecordMapper domainMapper;

    @Override
    public void insert(Product product) {
        ProductsPojo pojo = domainMapper.fromDomain(product);
        dsl.newRecord(PRODUCTS, pojo).insert();
    }

    @Override
    public Optional<Product> findById(UUID id, SelectMode mode) {
        var query = dsl.selectFrom(PRODUCTS).where(PRODUCTS.ID.eq(id));
        var pojo = (mode == SelectMode.FOR_UPDATE
                ? query.forUpdate().fetchOneInto(ProductsPojo.class)
                : query.fetchOneInto(ProductsPojo.class));
        return Optional.ofNullable(pojo).map(domainMapper::toDomain);
    }

    @Override
    public void updateStatus(UUID id, Product.Status newStatus, OffsetDateTime updatedAt) {
        dsl.update(PRODUCTS)
            .set(PRODUCTS.STATUS, toDb(newStatus))
            .set(PRODUCTS.UPDATED_AT, updatedAt)
            .where(PRODUCTS.ID.eq(id))
            .execute();
    }

    @Override
    public void updatePrice(UUID id, BigDecimal newPrice, OffsetDateTime updatedAt) {
        dsl.update(PRODUCTS)
            .set(PRODUCTS.PRICE, newPrice)
            .set(PRODUCTS.UPDATED_AT, updatedAt)
            .where(PRODUCTS.ID.eq(id))
            .execute();
    }

    @Override
    public PageView<Product> findBySeller(
        UUID sellerId,
        Product.Status statusFilterOrNull,
        int offset,
        int limit,
        ProductSortField sort
    ) {
        var cond = PRODUCTS.SELLER_ID.eq(sellerId);
        if (statusFilterOrNull != null) {
            cond = cond.and(PRODUCTS.STATUS.eq(toDb(statusFilterOrNull)));
        }
        long total = dsl.fetchCount(PRODUCTS, cond);
        List<Product> content = dsl.selectFrom(PRODUCTS)
            .where(cond)
            .orderBy(orderBy(sort))
            .offset(offset)
            .limit(limit)
            .fetchInto(ProductsPojo.class)
            .stream()
            .map(domainMapper::toDomain)
            .toList();
        int page = limit == 0 ? 1 : (offset / limit) + 1;
        return new PageView<>(content, page, limit, total);
    }

    private SortField<?> orderBy(ProductSortField sort) {
        return switch (sort) {
            case CREATED_AT_ASC -> PRODUCTS.CREATED_AT.asc();
            case CREATED_AT_DESC -> PRODUCTS.CREATED_AT.desc();
            case UPDATED_AT_ASC -> PRODUCTS.UPDATED_AT.asc();
            case UPDATED_AT_DESC -> PRODUCTS.UPDATED_AT.desc();
            case TITLE_ASC -> PRODUCTS.TITLE.asc();
            case TITLE_DESC -> PRODUCTS.TITLE.desc();
        };
    }

    private ProductStatus toDb(Product.Status s) {
        return switch (s) {
            case DRAFT -> ProductStatus.DRAFT;
            case PUBLISHED -> ProductStatus.PUBLISHED;
            case HIDDEN -> ProductStatus.HIDDEN;
        };
    }
}
