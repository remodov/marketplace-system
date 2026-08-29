package ru.vikulinva.orderservice.usecase.command;

import ru.vikulinva.usecase.cqrs.UseCaseCommand;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.List;
import java.util.Objects;

/**
 * UC-1 «Создание заказа». Записывает {@link Order} в статусе {@code DRAFT} и
 * публикует {@code OrderCreated}.
 *
 * <p>Идемпотентность по {@code idempotencyKey} (BR-010): повторный вызов с тем
 * же ключом и тем же телом возвращает прежний {@link Order}; с тем же ключом и
 * другим телом — {@code IdempotencyKeyConflictException}.
 *
 * <p>Возвращает корень агрегата — контроллер сам мапит в JsonBean.
 */
public record CreateOrderUseCase(
    CustomerId customerId,
    List<Item> items,
    Address shippingAddress,
    String idempotencyKey,
    String requestHash
) implements UseCaseCommand<Order> {

    public CreateOrderUseCase {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(shippingAddress, "shippingAddress");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestHash, "requestHash");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        items = List.copyOf(items);
    }

    /** Item приходит из API: только продуктовые ID и количество; цена будет резолвиться из Catalog. */
    public record Item(ProductId productId, SellerId sellerId, Quantity quantity) {

        public Item {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(sellerId, "sellerId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }
}
