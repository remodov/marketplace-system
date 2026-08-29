package ru.vikulinva.orderservice.usecase.query;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.hexagonal.InboundPort;
import ru.vikulinva.orderservice.port.out.OrderQueryPort;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;
import ru.vikulinva.usecase.UseCaseHandler;

import java.util.Objects;

/**
 * Обработчик UC-5 «Список заказов покупателя».
 */
@Component
@InboundPort
public class ListMyOrdersQueryHandler
    implements UseCaseHandler<ListMyOrdersQuery, PageResult<OrderSummary>> {

    private final OrderQueryPort orderQueryPort;

    public ListMyOrdersQueryHandler(OrderQueryPort orderQueryPort) {
        this.orderQueryPort = Objects.requireNonNull(orderQueryPort, "orderQueryPort");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderSummary> handle(ListMyOrdersQuery query) {
        return orderQueryPort.listByCustomer(
            query.requesterId(), query.statusFilter(), query.page(), query.size());
    }

    @Override
    public Class<ListMyOrdersQuery> useCaseType() {
        return ListMyOrdersQuery.class;
    }
}
