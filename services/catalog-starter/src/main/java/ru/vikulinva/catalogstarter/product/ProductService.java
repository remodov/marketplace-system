package ru.vikulinva.catalogstarter.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Product> search(String query) {
        return query == null || query.isBlank()
            ? repository.findAll()
            : repository.findByTitleContainingIgnoreCase(query.strip());
    }

    @Transactional(readOnly = true)
    public List<Product> cheaperThan(BigDecimal maxPrice) {
        return repository.findByPriceLessThanEqualOrderByPriceAsc(maxPrice);
    }

    @Transactional(readOnly = true)
    public Product byId(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(String title, BigDecimal price, int stock) {
        return repository.save(new Product(UUID.randomUUID(), title, price, stock));
    }

    @Transactional
    public Product changePrice(UUID id, BigDecimal newPrice) {
        Product product = byId(id);
        product.changePrice(newPrice);
        return product;
    }

    @Transactional
    public Product applyDiscount(UUID id, int percent) {
        Product product = byId(id);
        product.applyDiscount(percent);
        return product;
    }

    @Transactional
    public Product changeStock(UUID id, int delta) {
        Product product = byId(id);
        product.changeStock(delta);
        return product;
    }

    @Transactional
    public Product reserve(UUID id, int quantity) {
        Product product = byId(id);
        product.reserve(quantity);
        return product;
    }
}
