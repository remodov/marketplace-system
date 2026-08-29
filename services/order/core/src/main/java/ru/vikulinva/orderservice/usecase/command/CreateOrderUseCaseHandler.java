package ru.vikulinva.orderservice.usecase.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.port.out.CatalogPort;
import ru.vikulinva.orderservice.port.out.IdempotencyKeyRepository;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.service.UuidGenerator;
import ru.vikulinva.orderservice.usecase.command.exception.MultiSellerNotSupportedException;
import ru.vikulinva.orderservice.usecase.command.exception.ProductNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Обработчик UC-1 «Создание заказа».
 *
 * <p>Шаги:
 * <ol>
 *   <li>BR-014: проверить, что все позиции — от одного продавца.</li>
 *   <li>BR-010: если идемпотентный ключ уже виден — вернуть существующий заказ.</li>
 *   <li>Запросить актуальные цены через {@link CatalogPort}.</li>
 *   <li>Построить агрегат {@link Order} (метод {@code create} регистрирует {@code OrderCreated}).</li>
 *   <li>Сохранить через {@link OrderRepository#save(Order)} — атомарно с публикацией событий через Outbox.</li>
 *   <li>Записать idempotency-ключ.</li>
 * </ol>
 *
 * Транзакция (TS-1, TS-9): один {@code @Transactional} на handler.
 */
@Component
@InboundPort
public class CreateOrderUseCaseHandler implements UseCaseHandler<CreateOrderUseCase, Order> {

    private static final Money ZERO_SHIPPING = Money.zero(Money.RUB);

    private final OrderRepository orderRepository;
    private final CatalogPort catalogPort;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final UuidGenerator uuidGenerator;
    private final DateTimeService dateTimeService;

    public CreateOrderUseCaseHandler(OrderRepository orderRepository,
                                      CatalogPort catalogPort,
                                      IdempotencyKeyRepository idempotencyKeyRepository,
                                      UuidGenerator uuidGenerator,
                                      DateTimeService dateTimeService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.catalogPort = Objects.requireNonNull(catalogPort, "catalogPort");
        this.idempotencyKeyRepository = Objects.requireNonNull(idempotencyKeyRepository, "idempotencyKeyRepository");
        this.uuidGenerator = Objects.requireNonNull(uuidGenerator, "uuidGenerator");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Override
    @Transactional
    public Order handle(CreateOrderUseCase useCase) {
        // BR-014 — заранее, чтобы не дёргать Catalog зря.
        ensureSingleSeller(useCase.items());

        // BR-010 — идемпотентность: если ключ виден и хеш совпадает, отдаём прежний заказ.
        var existingOrderId = idempotencyKeyRepository.find(useCase.idempotencyKey(), useCase.requestHash());
        if (existingOrderId.isPresent()) {
            return orderRepository.findById(existingOrderId.get())
                .orElseThrow(() -> new IllegalStateException(
                    "Idempotency key points to non-existent order " + existingOrderId.get()));
        }

        // Запрос цен у Catalog.
        Map<ProductId, Money> prices = resolvePrices(useCase.items());

        // Сборка агрегата.
        Instant now = dateTimeService.now();
        OrderId orderId = OrderId.of(uuidGenerator.generate());
        List<OrderItem> orderItems = buildOrderItems(useCase.items(), prices);

        Order order = Order.create(
            orderId,
            useCase.customerId(),
            orderItems,
            ZERO_SHIPPING,                  // Доставка считается на этапе ConfirmOrder; для DRAFT — 0.
            useCase.shippingAddress(),
            now
        );

        orderRepository.save(order);
        idempotencyKeyRepository.save(useCase.idempotencyKey(), useCase.requestHash(), orderId, now);
        return order;
    }

    @Override
    public Class<CreateOrderUseCase> useCaseType() {
        return CreateOrderUseCase.class;
    }

    private static void ensureSingleSeller(List<CreateOrderUseCase.Item> items) {
        Set<SellerId> sellers = items.stream()
            .map(CreateOrderUseCase.Item::sellerId)
            .collect(Collectors.toUnmodifiableSet());
        if (sellers.size() > 1) {
            throw new MultiSellerNotSupportedException(sellers.size());
        }
    }

    private Map<ProductId, Money> resolvePrices(List<CreateOrderUseCase.Item> items) {
        List<ProductId> productIds = items.stream()
            .map(CreateOrderUseCase.Item::productId)
            .distinct()
            .toList();
        Map<ProductId, Money> prices = catalogPort.getPrices(productIds);
        List<ProductId> missing = productIds.stream()
            .filter(id -> !prices.containsKey(id))
            .toList();
        if (!missing.isEmpty()) {
            throw new ProductNotFoundException(missing);
        }
        return prices;
    }

    private List<OrderItem> buildOrderItems(List<CreateOrderUseCase.Item> items,
                                             Map<ProductId, Money> prices) {
        return items.stream()
            .map(i -> new OrderItem(
                OrderItemId.of(uuidGenerator.generate()),
                i.productId(),
                i.sellerId(),
                i.quantity(),
                prices.get(i.productId())
            ))
            .toList();
    }
}
