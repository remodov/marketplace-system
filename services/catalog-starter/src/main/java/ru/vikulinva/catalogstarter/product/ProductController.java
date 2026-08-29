package ru.vikulinva.catalogstarter.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    public record ProductView(UUID id, String title, BigDecimal price, int stock) {

        static ProductView of(Product product) {
            return new ProductView(product.getId(), product.getTitle(), product.getPrice(), product.getStock());
        }
    }

    public record CreateProductRequest(@NotBlank String title,
                                       @Positive BigDecimal price,
                                       @PositiveOrZero int stock) {
    }

    public record ReserveRequest(@Positive int quantity) {
    }

    // TODO шаг 3: тела запросов на смену цены и остатка.
    // Проверки входа вешаются здесь аннотациями, доменные правила — в сущности.

    @GetMapping
    public List<ProductView> search(@RequestParam(required = false) String query,
                                    @RequestParam(required = false) BigDecimal maxPrice) {
        List<Product> found = maxPrice == null ? service.search(query) : service.cheaperThan(maxPrice);
        return found.stream().map(ProductView::of).toList();
    }

    @GetMapping("/{id}")
    public ProductView byId(@PathVariable UUID id) {
        return ProductView.of(service.byId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@Valid @RequestBody CreateProductRequest request) {
        return ProductView.of(service.create(request.title(), request.price(), request.stock()));
    }

    // TODO шаг 3: PATCH /products/{id}/price и PATCH /products/{id}/stock.
    // Метод выбран не случайно: меняется часть товара, а не товар целиком.

    @PostMapping("/{id}/reserve")
    public ProductView reserve(@PathVariable UUID id, @Valid @RequestBody ReserveRequest request) {
        return ProductView.of(service.reserve(id, request.quantity()));
    }
}
