package ru.vikulinva.catalogstarter.search;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.catalogstarter.product.Product;
import ru.vikulinva.catalogstarter.product.ProductRepository;

import java.util.List;

@Service
public class NaturalSearchService {

    private final QueryUnderstanding understanding;
    private final ProductRepository repository;

    public NaturalSearchService(QueryUnderstanding understanding, ProductRepository repository) {
        this.understanding = understanding;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Product> search(String query) {
        SearchFilters filters = understanding.understand(query);

        List<Product> found = filters.text() == null || filters.text().isBlank()
            ? repository.findAll()
            : repository.findByTitleContainingIgnoreCase(filters.text().strip());

        return found.stream()
            .filter(p -> filters.maxPrice() == null || p.getPrice().compareTo(filters.maxPrice()) <= 0)
            .filter(p -> !filters.inStockOnly() || p.available() > 0)
            .toList();
    }
}
