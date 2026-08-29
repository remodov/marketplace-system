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

    public record CreateProductRequest(@NotBlank String title,
                                       @Positive BigDecimal price,
                                       @PositiveOrZero int stock) {
    }

    public record ReserveRequest(@Positive int quantity) {
    }

    public record ChangePriceRequest(@NotNull @Positive BigDecimal price) {
    }

    public record ChangeStockRequest(@NotNull Integer delta) {
    }

    public record ApplyDiscountRequest(@NotNull Integer percent) {
    }

    @GetMapping
    public List<ProductCard> search(@RequestParam(required = false) String query,
                                    @RequestParam(required = false) BigDecimal maxPrice) {
        List<Product> found = maxPrice == null ? service.search(query) : service.cheaperThan(maxPrice);
        return found.stream().map(ProductCard::of).toList();
    }

    @GetMapping("/{id}")
    public ProductCard byId(@PathVariable UUID id) {
        return service.card(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCard create(@Valid @RequestBody CreateProductRequest request) {
        return ProductCard.of(service.create(request.title(), request.price(), request.stock()));
    }

    @PatchMapping("/{id}/price")
    public ProductCard changePrice(@PathVariable UUID id, @Valid @RequestBody ChangePriceRequest request) {
        return ProductCard.of(service.changePrice(id, request.price()));
    }

    @PatchMapping("/{id}/discount")
    public ProductCard applyDiscount(@PathVariable UUID id, @Valid @RequestBody ApplyDiscountRequest request) {
        return ProductCard.of(service.applyDiscount(id, request.percent()));
    }

    @PatchMapping("/{id}/stock")
    public ProductCard changeStock(@PathVariable UUID id, @Valid @RequestBody ChangeStockRequest request) {
        return ProductCard.of(service.changeStock(id, request.delta()));
    }

    @PostMapping("/{id}/reserve")
    public ProductCard reserve(@PathVariable UUID id, @Valid @RequestBody ReserveRequest request) {
        return ProductCard.of(service.reserve(id, request.quantity()));
    }
}
