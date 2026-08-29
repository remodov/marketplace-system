package ru.vikulinva.orderservice.usecase.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.exception.OrderInvalidStateException;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;

/**
 * Обработчик OpenDispute. ABAC: только владелец заказа.
 */
@Component
@InboundPort
public class OpenDisputeUseCaseHandler implements UseCaseHandler<OpenDisputeUseCase, Order> {

    private final OrderRepository orderRepository;
    private final DateTimeService dateTimeService;

    public OpenDisputeUseCaseHandler(OrderRepository orderRepository, DateTimeService dateTimeService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Override
    @Transactional
    public Order handle(OpenDisputeUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        if (!order.customerId().equals(useCase.requesterId())) {
            throw new OrderNotFoundException(useCase.orderId());
        }

        try {
            order.openDispute(useCase.reason(), dateTimeService.now());
        } catch (IllegalStateException e) {
            throw new OrderInvalidStateException(order.status(), OrderStatus.DELIVERED, "open dispute");
        }

        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<OpenDisputeUseCase> useCaseType() {
        return OpenDisputeUseCase.class;
    }
}
