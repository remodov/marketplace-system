package ru.vikulinva.orderservice.usecase.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.port.out.PaymentPort;
import ru.vikulinva.orderservice.usecase.command.exception.OrderInvalidStateException;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;
import java.util.UUID;

/**
 * Обработчик UC-3 «Отмена заказа».
 *
 * <p>Шаги:
 * <ol>
 *   <li>Загрузить заказ; нет — {@code OrderNotFoundException}.</li>
 *   <li>ABAC: владелец совпадает с {@code requesterId}; нет — {@code OrderNotFoundException}.</li>
 *   <li>{@link Order#cancel(ru.vikulinva.orderservice.domain.valueobject.CancellationReason)} —
 *       перевод в CANCELLED, событие OrderCancelled.</li>
 *   <li>Сохранить через {@link OrderRepository#save(Order)} (Outbox).</li>
 * </ol>
 */
@Component
@InboundPort
public class CancelOrderUseCaseHandler implements UseCaseHandler<CancelOrderUseCase, Order> {

    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;

    public CancelOrderUseCaseHandler(OrderRepository orderRepository, PaymentPort paymentPort) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.paymentPort = Objects.requireNonNull(paymentPort, "paymentPort");
    }

    @Override
    @Transactional
    public Order handle(CancelOrderUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        if (!order.customerId().equals(useCase.requesterId())) {
            throw new OrderNotFoundException(useCase.orderId());
        }

        try {
            if (order.status() == OrderStatus.PAID) {
                UUID refundId = paymentPort.requestRefund(
                    order.getId(),
                    order.paymentId(),
                    order.total(),
                    "refund-" + order.getId().value());
                order.cancelAfterPayment(useCase.reason(), refundId);
            } else {
                order.cancel(useCase.reason());
            }
        } catch (IllegalStateException e) {
            throw new OrderInvalidStateException(order.status(), OrderStatus.PENDING_PAYMENT, "cancel");
        }

        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<CancelOrderUseCase> useCaseType() {
        return CancelOrderUseCase.class;
    }
}
