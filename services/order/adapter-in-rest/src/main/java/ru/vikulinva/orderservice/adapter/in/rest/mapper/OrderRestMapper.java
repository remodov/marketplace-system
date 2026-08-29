package ru.vikulinva.orderservice.adapter.in.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.Address;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.CreateOrderItem;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.CreateOrderRequest;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.Order;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderItem;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Маппинг JsonBean (DTO из OpenAPI) ↔ доменные/UseCase типы.
 *
 * <p>Контроллер не должен инжектить ObjectMapper или знать поля DTO —
 * вся логика преобразования здесь.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderRestMapper {

    // ---- request -----------------------------------------------------------

    default CreateOrderUseCase toUseCase(CustomerId customerId,
                                          CreateOrderRequest request,
                                          String idempotencyKey,
                                          String requestHash) {
        ru.vikulinva.orderservice.domain.valueobject.Address shippingAddress =
            toDomainAddress(request.getShippingAddress());
        List<CreateOrderUseCase.Item> items = request.getItems().stream()
            .map(this::toUseCaseItem)
            .toList();
        return new CreateOrderUseCase(customerId, items, shippingAddress, idempotencyKey, requestHash);
    }

    default CreateOrderUseCase.Item toUseCaseItem(CreateOrderItem dto) {
        return new CreateOrderUseCase.Item(
            ProductId.of(dto.getProductId()),
            SellerId.of(dto.getSellerId()),
            Quantity.of(dto.getQuantity())
        );
    }

    default ru.vikulinva.orderservice.domain.valueobject.Address toDomainAddress(Address dto) {
        return new ru.vikulinva.orderservice.domain.valueobject.Address(
            dto.getCountry(),
            dto.getCity(),
            dto.getStreet(),
            dto.getPostalCode(),
            dto.getPickupCode()
        );
    }

    // ---- response ----------------------------------------------------------

    default Order toRest(ru.vikulinva.orderservice.domain.aggregate.Order order) {
        Order dto = new Order();
        dto.setId(order.getId().value());
        dto.setCustomerId(order.customerId().value());
        dto.setSellerId(order.sellerId().value());
        dto.setStatus(toRestStatus(order.status()));
        dto.setCurrency(order.shippingFee().currency().getCurrencyCode());
        dto.setTotalAmount(monetaryToString(order.total()));
        dto.setShippingFee(monetaryToString(order.shippingFee()));
        dto.setItems(order.items().stream().map(this::toRestItem).toList());
        dto.setShippingAddress(toRestAddress(order.shippingAddress()));
        dto.setReservationId(null);
        dto.setPaymentId(order.paymentId());
        dto.setCreatedAt(toOffset(order.createdAt()));
        dto.setUpdatedAt(toOffset(order.createdAt()));
        return dto;
    }

    default OrderItem toRestItem(ru.vikulinva.orderservice.domain.entity.OrderItem item) {
        OrderItem dto = new OrderItem();
        dto.setId(item.getId().value());
        dto.setProductId(item.productId().value());
        dto.setSellerId(item.sellerId().value());
        dto.setQuantity(item.quantity().value());
        dto.setUnitPrice(monetaryToString(item.unitPrice()));
        return dto;
    }

    default Address toRestAddress(ru.vikulinva.orderservice.domain.valueobject.Address a) {
        Address dto = new Address();
        dto.setCountry(a.country());
        dto.setCity(a.city());
        dto.setStreet(a.street());
        dto.setPostalCode(a.postalCode());
        dto.setPickupCode(a.pickupCode());
        return dto;
    }

    default ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummary toRest(
        ru.vikulinva.orderservice.usecase.query.dto.OrderSummary summary) {
        var dto = new ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummary();
        dto.setId(summary.id().value());
        dto.setCustomerId(summary.customerId().value());
        dto.setSellerId(summary.sellerId().value());
        dto.setStatus(toSummaryStatus(summary.status()));
        dto.setCurrency(summary.total().currency().getCurrencyCode());
        dto.setTotalAmount(monetaryToString(summary.total()));
        dto.setItemsCount(summary.itemsCount());
        dto.setCreatedAt(toOffset(summary.createdAt()));
        return dto;
    }

    default ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummaryPage toRest(
        ru.vikulinva.orderservice.usecase.query.dto.PageResult<ru.vikulinva.orderservice.usecase.query.dto.OrderSummary> page) {
        var dto = new ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummaryPage();
        dto.setItems(page.items().stream().map(this::toRest).toList());
        dto.setTotal(page.total());
        dto.setPage(page.page());
        dto.setSize(page.size());
        dto.setHasNext(page.hasNext());
        return dto;
    }

    default ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummary.StatusEnum toSummaryStatus(
        ru.vikulinva.orderservice.domain.valueobject.OrderStatus status) {
        return ru.vikulinva.orderservice.adapter.in.rest.generated.model.OrderSummary.StatusEnum.valueOf(status.name());
    }

    @Named("monetaryToString")
    default String monetaryToString(Money money) {
        BigDecimal amount = money.amount();
        return amount.toPlainString();
    }

    default OrderStatus toRestStatus(ru.vikulinva.orderservice.domain.valueobject.OrderStatus status) {
        return OrderStatus.valueOf(status.name());
    }

    default OffsetDateTime toOffset(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    // helpers, нужны MapStruct-ом

    default UUID uuidFromUuid(UUID value) {
        return value;
    }
}
