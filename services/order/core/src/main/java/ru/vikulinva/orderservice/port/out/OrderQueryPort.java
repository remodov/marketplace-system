package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;

import java.time.Instant;
import java.util.List;

/**
 * Read-side порт для проекций заказов. Реализация — jOOQ в
 * {@code adapter-out-postgres} (без загрузки полного агрегата).
 */
@OutboundPort
public interface OrderQueryPort {

    /**
     * Список заказов покупателя с фильтром по статусу. Сортировка —
     * {@code createdAt DESC} (новые сверху). Параметры пагинации:
     * {@code page} ≥ 0, {@code size} ∈ [1; 100].
     *
     * @param status опциональный фильтр; {@code null} — без фильтра
     */
    PageResult<OrderSummary> listByCustomer(CustomerId customerId,
                                              OrderStatus status,
                                              int page,
                                              int size);

    /** То же, но с фильтром по продавцу — для seller-кабинета. */
    PageResult<OrderSummary> listBySeller(SellerId sellerId,
                                            OrderStatus status,
                                            int page,
                                            int size);

    /**
     * id заказов в статусе PENDING_PAYMENT с {@code created_at < threshold}.
     * Используется планировщиком ExpireUnpaid (BR-006).
     */
    List<OrderId> findExpiredPendingPayment(Instant threshold, int limit);

    /**
     * id заказов в статусе DELIVERED с {@code delivered_at < threshold}.
     * Используется планировщиком CloseDelivered.
     */
    List<OrderId> findStaleDelivered(Instant threshold, int limit);
}
