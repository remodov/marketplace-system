package ru.vikulinva.catalogstarter.product;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTitleContainingIgnoreCase(String part);

    // TODO шаг 5: выборка товара под блокировку строки.
    // Обычный findById для резерва не годится — почему, покажет тест на сто покупателей.

    List<Product> findByPriceLessThanEqualOrderByPriceAsc(BigDecimal maxPrice);
}
