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
 * Обработчик UC «Список заказов продавца».
 */
@Component
@InboundPort
public class ListSellerOrdersQueryHandler
    implements UseCaseHandler<ListSellerOrdersQuery, PageResult<OrderSummary>> {

    private final OrderQueryPort orderQueryPort;

    public ListSellerOrdersQueryHandler(OrderQueryPort orderQueryPort) {
        this.orderQueryPort = Objects.requireNonNull(orderQueryPort, "orderQueryPort");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderSummary> handle(ListSellerOrdersQuery query) {
        return orderQueryPort.listBySeller(
            query.requesterSellerId(), query.statusFilter(), query.page(), query.size());
    }

    @Override
    public Class<ListSellerOrdersQuery> useCaseType() {
        return ListSellerOrdersQuery.class;
    }
}
