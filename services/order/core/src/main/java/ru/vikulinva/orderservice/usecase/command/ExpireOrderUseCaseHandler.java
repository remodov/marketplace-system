package ru.vikulinva.orderservice.usecase.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;

/**
 * Обработчик ExpireOrderUseCase. Толерантен к гонкам: если на момент
 * обработки заказ уже не PENDING_PAYMENT (успели оплатить / отменить) —
 * молча возвращаем актуальный заказ, не падаем.
 */
@Component
@InboundPort
public class ExpireOrderUseCaseHandler implements UseCaseHandler<ExpireOrderUseCase, Order> {

    private final OrderRepository orderRepository;
    private final DateTimeService dateTimeService;

    public ExpireOrderUseCaseHandler(OrderRepository orderRepository, DateTimeService dateTimeService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Override
    @Transactional
    public Order handle(ExpireOrderUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            return order;
        }

        order.expire(dateTimeService.now());
        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<ExpireOrderUseCase> useCaseType() {
        return ExpireOrderUseCase.class;
    }
}
