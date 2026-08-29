package ru.vikulinva.orderservice.testutil.generator;

import ru.vikulinva.orderservice.adapter.out.postgres.generated.enums.OrderStatus;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.JSONB;

/**
 * Builder для {@link OrdersPojo} с разумными дефолтами (TS-12, TS-13).
 * {@code withNano(0)} обязательно — иначе сравнения с БД ломаются (TS-14).
 */
public final class OrderTestObjectGenerator {

    private UUID id = UUID.randomUUID();
    private UUID customerId = UUID.randomUUID();
    private UUID sellerId = UUID.randomUUID();
    private OrderStatus status = OrderStatus.DRAFT;
    private String currency = "RUB";
    private BigDecimal totalAmount = new BigDecimal("0.00");
    private BigDecimal shippingFee = new BigDecimal("0.00");
    private BigDecimal discountAmount = null;
    private JSONB shippingAddress = JSONB.valueOf(
        "{\"country\":\"RU\",\"city\":\"Moscow\",\"street\":\"Tverskaya 1\",\"postalCode\":\"125009\"}");
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    private OffsetDateTime updatedAt = createdAt;

    public OrderTestObjectGenerator withId(UUID id) { this.id = id; return this; }
    public OrderTestObjectGenerator withCustomerId(UUID id) { this.customerId = id; return this; }
    public OrderTestObjectGenerator withSellerId(UUID id) { this.sellerId = id; return this; }
    public OrderTestObjectGenerator withStatus(OrderStatus s) { this.status = s; return this; }
    public OrderTestObjectGenerator withTotalAmount(String amount) {
        this.totalAmount = new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
        return this;
    }
    public OrderTestObjectGenerator withShippingFee(String amount) {
        this.shippingFee = new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
        return this;
    }
    public OrderTestObjectGenerator withCreatedAt(OffsetDateTime t) {
        this.createdAt = t.withNano(0);
        this.updatedAt = this.createdAt;
        return this;
    }

    public OrdersPojo generate() {
        OrdersPojo p = new OrdersPojo();
        p.setId(id);
        p.setCustomerId(customerId);
        p.setSellerId(sellerId);
        p.setStatus(status);
        p.setCurrency(currency);
        p.setTotalAmount(totalAmount);
        p.setShippingFee(shippingFee);
        p.setDiscountAmount(discountAmount);
        p.setShippingAddress(shippingAddress);
        p.setCreatedAt(createdAt);
        p.setUpdatedAt(updatedAt);
        return p;
    }
}
