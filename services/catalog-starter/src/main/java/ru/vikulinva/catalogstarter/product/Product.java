package ru.vikulinva.catalogstarter.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    public static final int MAX_DISCOUNT_PERCENT = 50;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    // TODO шаг 5: поле под зарезервированное количество.

    @Version
    private long version;

    protected Product() {
    }

    public Product(UUID id, String title, BigDecimal price, int stock) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.stock = stock;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    // TODO шаг 5: сколько зарезервировано и сколько реально можно продать.
    public int available() {
        throw new UnsupportedOperationException("Шаг 5: резерв ещё не отделён от остатка");
    }

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new IllegalArgumentException("Цена должна быть больше нуля");
        }
        this.price = newPrice;
    }

    public void applyDiscount(int percent) {
        if (percent < 1 || percent > MAX_DISCOUNT_PERCENT) {
            throw new IllegalArgumentException(
                "Скидка допустима от 1 до " + MAX_DISCOUNT_PERCENT + " процентов, а не " + percent);
        }
        BigDecimal multiplier = BigDecimal.valueOf(100 - percent).divide(BigDecimal.valueOf(100));
        this.price = price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public void changeStock(int delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("Изменение остатка не может быть нулевым");
        }
        // TODO шаг 5: списывать можно только то, что не удержано резервом.
        if (stock + delta < 0) {
            throw new OutOfStockException(id, -delta, stock);
        }
        stock += delta;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше нуля");
        }
        // TODO шаг 5: резерв удерживает товар, а не списывает его со склада.
        if (quantity > stock) {
            throw new OutOfStockException(id, quantity, stock);
        }
        stock -= quantity;
    }
}
