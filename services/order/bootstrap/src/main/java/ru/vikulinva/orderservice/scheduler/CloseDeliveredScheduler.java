package ru.vikulinva.orderservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.OrderQueryPort;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.usecase.command.CompleteOrderUseCase;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.time.Duration;
import java.util.List;

/**
 * Планировщик CloseDelivered: ночью переводит заказы DELIVERED &gt;
 * {@code orderservice.close-delivered.window-days} в COMPLETED.
 * Cron-выражение настраивается через {@code orderservice.close-delivered.cron}.
 */
@Component
public class CloseDeliveredScheduler {

    private static final Logger log = LoggerFactory.getLogger(CloseDeliveredScheduler.class);

    private final OrderQueryPort orderQueryPort;
    private final UseCaseDispatcher useCaseDispatcher;
    private final DateTimeService dateTimeService;
    private final Duration window;
    private final int batchSize;

    public CloseDeliveredScheduler(OrderQueryPort orderQueryPort,
                                     UseCaseDispatcher useCaseDispatcher,
                                     DateTimeService dateTimeService,
                                     @Value("${orderservice.close-delivered.window-days:14}") int windowDays,
                                     @Value("${orderservice.close-delivered.batch-size:1000}") int batchSize) {
        this.orderQueryPort = orderQueryPort;
        this.useCaseDispatcher = useCaseDispatcher;
        this.dateTimeService = dateTimeService;
        this.window = Duration.ofDays(windowDays);
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${orderservice.close-delivered.cron:0 0 4 * * *}")
    public void tick() {
        try {
            List<OrderId> stale = orderQueryPort.findStaleDelivered(
                dateTimeService.now().minus(window), batchSize);
            for (OrderId orderId : stale) {
                try {
                    useCaseDispatcher.dispatch(new CompleteOrderUseCase(orderId));
                } catch (Exception ex) {
                    log.warn("CloseDelivered failed for order {}: {}", orderId.value(), ex.getMessage());
                }
            }
            if (!stale.isEmpty()) {
                log.info("CloseDelivered processed {} orders", stale.size());
            }
        } catch (Exception e) {
            log.warn("CloseDelivered tick failed: {}", e.getMessage(), e);
        }
    }
}
