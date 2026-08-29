package ru.vikulinva.orderservice.usecase.query;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;

/**
 * Обработчик UC-4 «Чтение заказа по идентификатору».
 *
 * <p>Транзакция read-only. ABAC: владелец совпадает с {@code requesterId};
 * иначе — {@code OrderNotFoundException}.
 */
@Component
@InboundPort
public class GetOrderByIdQueryHandler implements UseCaseHandler<GetOrderByIdQuery, Order> {

    private final OrderRepository orderRepository;

    public GetOrderByIdQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
    }

    @Override
    @Transactional(readOnly = true)
    public Order handle(GetOrderByIdQuery query) {
        Order order = orderRepository.findById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        if (!order.customerId().equals(query.requesterId())) {
            throw new OrderNotFoundException(query.orderId());
        }
        return order;
    }

    @Override
    public Class<GetOrderByIdQuery> useCaseType() {
        return GetOrderByIdQuery.class;
    }
}
