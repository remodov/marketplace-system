package ru.vikulinva.catalogstarter.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTitleContainingIgnoreCase(String part);

    List<Product> findByPriceLessThanEqualOrderByPriceAsc(BigDecimal maxPrice);
}
