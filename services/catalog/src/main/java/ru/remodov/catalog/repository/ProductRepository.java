package ru.remodov.catalog.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import ru.remodov.catalog.domain.PageView;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.domain.ProductSortField;

public interface ProductRepository {

    void insert(Product product);

    Optional<Product> findById(UUID id, SelectMode mode);

    void updateStatus(UUID id, Product.Status newStatus, OffsetDateTime updatedAt);

    // TODO шаг 7: метод порта под смену цены. Порт описывает, что нужно use case'у,
    // а не что умеет база — отсюда и сигнатура.

    PageView<Product> findBySeller(
        UUID sellerId,
        Product.Status statusFilterOrNull,
        int offset,
        int limit,
        ProductSortField sort
    );

    enum SelectMode { NO_LOCK, FOR_UPDATE }
}
