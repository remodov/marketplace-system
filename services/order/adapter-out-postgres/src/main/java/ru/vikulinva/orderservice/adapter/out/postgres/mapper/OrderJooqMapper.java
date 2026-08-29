package ru.vikulinva.orderservice.adapter.out.postgres.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrderItemsPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

/**
 * Маппинг между доменным {@link Order}/{@link OrderItem} и сгенерированными
 * jOOQ Pojo. Адрес сериализуется как JSONB.
 */
@Component
public class OrderJooqMapper {

    private final ObjectMapper objectMapper;

    public OrderJooqMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ----- to Pojo (write) --------------------------------------------------

    public OrdersPojo toPojo(Order order) {
        OrdersPojo pojo = new OrdersPojo();
        pojo.setId(order.getId().value());
        pojo.setCustomerId(order.customerId().value());
        pojo.setSellerId(order.sellerId().value());
        pojo.setStatus(toGeneratedStatus(order.status()));
        pojo.setCurrency(order.shippingFee().currency().getCurrencyCode());
        pojo.setTotalAmount(order.total().amount());
        pojo.setShippingFee(order.shippingFee().amount());
        pojo.setShippingAddress(toJsonb(order.shippingAddress()));
        pojo.setCreatedAt(toOffset(order.createdAt().toEpochMilli()));
        pojo.setUpdatedAt(toOffset(order.createdAt().toEpochMilli()));
        pojo.setPaymentId(order.paymentId());
        if (order.paidAt() != null) {
            pojo.setPaidAt(toOffset(order.paidAt().toEpochMilli()));
        }
        if (order.shippedAt() != null) {
            pojo.setShippedAt(toOffset(order.shippedAt().toEpochMilli()));
        }
        if (order.deliveredAt() != null) {
            pojo.setDeliveredAt(toOffset(order.deliveredAt().toEpochMilli()));
        }
        if (order.closedAt() != null) {
            pojo.setClosedAt(toOffset(order.closedAt().toEpochMilli()));
        }
        return pojo;
    }

    public List<OrderItemsPojo> toItemsPojo(Order order) {
        return order.items().stream()
            .map(i -> {
                OrderItemsPojo p = new OrderItemsPojo();
                p.setId(i.getId().value());
                p.setOrderId(order.getId().value());
                p.setProductId(i.productId().value());
                p.setSellerId(i.sellerId().value());
                p.setQuantity(i.quantity().value());
                p.setUnitPrice(i.unitPrice().amount());
                return p;
            })
            .toList();
    }

    // ----- from Pojo (read) -------------------------------------------------

    public Order toDomain(OrdersPojo orderPojo, List<OrderItemsPojo> itemPojos) {
        List<OrderItem> items = itemPojos.stream()
            .map(this::toDomainItem)
            .toList();
        Currency currency = Currency.getInstance(orderPojo.getCurrency().trim());
        Money shippingFee = new Money(orderPojo.getShippingFee(), currency);
        return Order.restore(
            OrderId.of(orderPojo.getId()),
            CustomerId.of(orderPojo.getCustomerId()),
            SellerId.of(orderPojo.getSellerId()),
            ru.vikulinva.orderservice.domain.valueobject.OrderStatus.valueOf(orderPojo.getStatus().getLiteral()),
            items,
            null,                                       // discount: на UC-1 пусто; добавим в UC ApplyPromo
            shippingFee,
            fromJsonb(orderPojo.getShippingAddress()),
            orderPojo.getCreatedAt().toInstant(),
            orderPojo.getPaymentId(),
            orderPojo.getPaidAt() != null ? orderPojo.getPaidAt().toInstant() : null,
            orderPojo.getShippedAt() != null ? orderPojo.getShippedAt().toInstant() : null,
            orderPojo.getDeliveredAt() != null ? orderPojo.getDeliveredAt().toInstant() : null,
            orderPojo.getClosedAt() != null ? orderPojo.getClosedAt().toInstant() : null
        );
    }

    private OrderItem toDomainItem(OrderItemsPojo p) {
        Money price = new Money(p.getUnitPrice(), Money.RUB);
        return new OrderItem(
            OrderItemId.of(p.getId()),
            ProductId.of(p.getProductId()),
            SellerId.of(p.getSellerId()),
            Quantity.of(p.getQuantity()),
            price
        );
    }

    // ----- helpers ----------------------------------------------------------

    private ru.vikulinva.orderservice.adapter.out.postgres.generated.enums.OrderStatus toGeneratedStatus(
            ru.vikulinva.orderservice.domain.valueobject.OrderStatus s) {
        return ru.vikulinva.orderservice.adapter.out.postgres.generated.enums.OrderStatus.valueOf(s.name());
    }

    private JSONB toJsonb(Address address) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(address));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Address", e);
        }
    }

    private Address fromJsonb(JSONB json) {
        try {
            return objectMapper.readValue(json.data(), Address.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize Address", e);
        }
    }

    private static OffsetDateTime toOffset(long epochMilli) {
        return java.time.Instant.ofEpochMilli(epochMilli).atOffset(ZoneOffset.UTC);
    }

    /** BigDecimal helper для тестов. */
    public BigDecimal scale(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
