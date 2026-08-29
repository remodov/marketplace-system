package ru.vikulinva.notificationservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.service.NotificationService;

import java.util.Objects;

/**
 * Периодически забирает QUEUED-уведомления и отправляет.
 */
@Component
public class DispatchPendingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchPendingScheduler.class);

    private final NotificationService notificationService;
    private final int batchSize;

    public DispatchPendingScheduler(NotificationService notificationService,
                                      @Value("${notificationservice.dispatch.batch-size:100}") int batchSize) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notificationservice.dispatch.fixed-delay-ms:1000}")
    public void tick() {
        try {
            int sent = notificationService.dispatchPending(batchSize);
            if (sent > 0) {
                log.debug("Dispatch tick: sent {} notifications", sent);
            }
        } catch (Exception e) {
            log.warn("Dispatch tick failed: {}", e.getMessage(), e);
        }
    }
}
