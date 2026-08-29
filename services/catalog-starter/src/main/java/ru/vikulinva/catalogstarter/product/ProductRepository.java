package ru.vikulinva.catalogstarter.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTitleContainingIgnoreCase(String part);

    // TODO шаг 2: объявить метод поиска товаров не дороже указанной цены.
    // Spring Data выводит запрос из имени метода — SQL писать не нужно.
    // Порядок выдачи задаётся тем же именем.
}
