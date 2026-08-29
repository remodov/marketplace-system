package ru.vikulinva.catalogstarter.product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    static final String CARDS = "product-cards";

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

    @Cacheable(CARDS)
    @Transactional(readOnly = true)
    public ProductCard card(UUID id) {
        return ProductCard.of(byId(id));
    }

    @Transactional(readOnly = true)
    public Product byId(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(String title, BigDecimal price, int stock) {
        return repository.save(new Product(UUID.randomUUID(), title, price, stock));
    }

    @CacheEvict(cacheNames = CARDS, key = "#id")
    @Transactional
    public Product changePrice(UUID id, BigDecimal newPrice) {
        Product product = byId(id);
        product.changePrice(newPrice);
        return product;
    }

    @CacheEvict(cacheNames = CARDS, key = "#id")
    @Transactional
    public Product applyDiscount(UUID id, int percent) {
        Product product = byId(id);
        product.applyDiscount(percent);
        return product;
    }

    @CacheEvict(cacheNames = CARDS, key = "#id")
    @Transactional
    public Product changeStock(UUID id, int delta) {
        Product product = byId(id);
        product.changeStock(delta);
        return product;
    }

    @CacheEvict(cacheNames = CARDS, key = "#id")
    @Transactional
    public Product reserve(UUID id, int quantity) {
        Product product = repository.findForUpdate(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.reserve(quantity);
        return product;
    }
}
