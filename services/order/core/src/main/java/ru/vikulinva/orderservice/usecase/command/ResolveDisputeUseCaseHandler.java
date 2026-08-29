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
 * Обработчик ResolveDispute. ABAC проверяется в REST-контроллере
 * (роль {@code dispute-operator}/{@code admin}).
 */
@Component
@InboundPort
public class ResolveDisputeUseCaseHandler implements UseCaseHandler<ResolveDisputeUseCase, Order> {

    private final OrderRepository orderRepository;
    private final DateTimeService dateTimeService;

    public ResolveDisputeUseCaseHandler(OrderRepository orderRepository, DateTimeService dateTimeService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Override
    @Transactional
    public Order handle(ResolveDisputeUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        try {
            order.resolveDispute(useCase.finalStatus(), useCase.resolutionNote(), dateTimeService.now());
        } catch (IllegalStateException e) {
            throw new OrderInvalidStateException(order.status(), OrderStatus.DISPUTE, "resolve dispute");
        }

        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<ResolveDisputeUseCase> useCaseType() {
        return ResolveDisputeUseCase.class;
    }
}
