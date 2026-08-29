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
 * Обработчик UC-6. Идемпотентен: повторный вебхук с тем же {@code paymentId}
 * на уже-PAID заказе ничего не делает.
 */
@Component
@InboundPort
public class PayOrderUseCaseHandler implements UseCaseHandler<PayOrderUseCase, Order> {

    private final OrderRepository orderRepository;
    private final DateTimeService dateTimeService;

    public PayOrderUseCaseHandler(OrderRepository orderRepository, DateTimeService dateTimeService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
    }

    @Override
    @Transactional
    public Order handle(PayOrderUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        if (order.status() == OrderStatus.PAID
            && useCase.paymentId().equals(order.paymentId())) {
            return order;
        }

        try {
            order.markPaid(useCase.paymentId(), dateTimeService.now());
        } catch (IllegalStateException e) {
            throw new OrderInvalidStateException(order.status(), OrderStatus.PENDING_PAYMENT, "pay");
        }

        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<PayOrderUseCase> useCaseType() {
        return PayOrderUseCase.class;
    }
}
