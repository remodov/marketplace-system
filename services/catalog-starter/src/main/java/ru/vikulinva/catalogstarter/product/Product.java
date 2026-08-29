package ru.vikulinva.catalogstarter.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

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

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new IllegalArgumentException("Цена должна быть больше нуля");
        }
        this.price = newPrice;
    }

    public void changeStock(int delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("Изменение остатка не может быть нулевым");
        }
        if (stock + delta < 0) {
            throw new OutOfStockException(id, -delta, stock);
        }
        stock += delta;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше нуля");
        }
        if (quantity > stock) {
            throw new OutOfStockException(id, quantity, stock);
        }
        stock -= quantity;
    }
}
