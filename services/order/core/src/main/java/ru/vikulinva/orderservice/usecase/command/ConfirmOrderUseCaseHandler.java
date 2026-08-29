package ru.vikulinva.orderservice.usecase.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.usecase.command.exception.OrderBelowMinimumException;
import ru.vikulinva.orderservice.usecase.command.exception.OrderInvalidStateException;
import ru.vikulinva.orderservice.usecase.command.exception.OrderNotFoundException;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;

/**
 * Обработчик UC-2 «Подтверждение заказа».
 *
 * <p>Шаги:
 * <ol>
 *   <li>Загрузить {@link Order} по идентификатору; нет — {@code OrderNotFoundException}.</li>
 *   <li>ABAC: проверить, что заказ принадлежит {@code requesterId}; нет — тоже
 *       {@code OrderNotFoundException} (скрываем чужие заказы).</li>
 *   <li>Вызвать {@link Order#confirm()} — внутри проверяется статус (BR-...) и
 *       BR-013 (минимальная сумма), регистрируется {@code OrderConfirmed}.</li>
 *   <li>Сохранить через {@link OrderRepository#save(Order)} —
 *       публикация события атомарно через Outbox.</li>
 * </ol>
 *
 * Транзакция (TS-1, TS-9): один {@code @Transactional} на handler.
 */
@Component
@InboundPort
public class ConfirmOrderUseCaseHandler implements UseCaseHandler<ConfirmOrderUseCase, Order> {

    private final OrderRepository orderRepository;

    public ConfirmOrderUseCaseHandler(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
    }

    @Override
    @Transactional
    public Order handle(ConfirmOrderUseCase useCase) {
        Order order = orderRepository.findById(useCase.orderId())
            .orElseThrow(() -> new OrderNotFoundException(useCase.orderId()));

        if (!order.customerId().equals(useCase.requesterId())) {
            throw new OrderNotFoundException(useCase.orderId());
        }

        try {
            order.confirm();
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("BR-013")) {
                throw new OrderBelowMinimumException(message);
            }
            throw new OrderInvalidStateException(order.status(), OrderStatus.DRAFT, "confirm");
        }

        orderRepository.save(order);
        return order;
    }

    @Override
    public Class<ConfirmOrderUseCase> useCaseType() {
        return ConfirmOrderUseCase.class;
    }
}
