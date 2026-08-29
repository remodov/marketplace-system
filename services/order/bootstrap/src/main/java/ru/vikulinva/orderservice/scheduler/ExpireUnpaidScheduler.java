package ru.vikulinva.orderservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.OrderQueryPort;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.ExpireOrderUseCase;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Duration;
import java.util.List;

/**
 * Планировщик ExpireUnpaid (BR-006). Каждую минуту находит заказы в
 * PENDING_PAYMENT, висящие дольше {@code orderservice.expire-unpaid.timeout-minutes},
 * и переводит их в EXPIRED через {@link ExpireOrderUseCase}.
 *
 * <p>Шаг сделан с лимитом ({@code batch-size}), чтобы один тик не блокировал
 * базу при больших всплесках. На следующем тике обработка продолжится.
 */
@Component
public class ExpireUnpaidScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpireUnpaidScheduler.class);

    private final OrderQueryPort orderQueryPort;
    private final UseCaseDispatcher useCaseDispatcher;
    private final DateTimeService dateTimeService;
    private final Duration timeout;
    private final int batchSize;

    public ExpireUnpaidScheduler(OrderQueryPort orderQueryPort,
                                   UseCaseDispatcher useCaseDispatcher,
                                   DateTimeService dateTimeService,
                                   @Value("${orderservice.expire-unpaid.timeout-minutes:15}") int timeoutMinutes,
                                   @Value("${orderservice.expire-unpaid.batch-size:200}") int batchSize) {
        this.orderQueryPort = orderQueryPort;
        this.useCaseDispatcher = useCaseDispatcher;
        this.dateTimeService = dateTimeService;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderservice.expire-unpaid.fixed-delay-ms:60000}")
    public void tick() {
        try {
            List<OrderId> expired = orderQueryPort.findExpiredPendingPayment(
                dateTimeService.now().minus(timeout), batchSize);
            for (OrderId orderId : expired) {
                try {
                    useCaseDispatcher.dispatch(new ExpireOrderUseCase(orderId));
                } catch (Exception ex) {
                    log.warn("ExpireUnpaid failed for order {}: {}", orderId.value(), ex.getMessage());
                }
            }
            if (!expired.isEmpty()) {
                log.info("ExpireUnpaid processed {} orders", expired.size());
            }
        } catch (Exception e) {
            log.warn("ExpireUnpaid tick failed: {}", e.getMessage(), e);
        }
    }
}
