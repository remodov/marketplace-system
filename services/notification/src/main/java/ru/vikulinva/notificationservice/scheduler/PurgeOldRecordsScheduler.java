package ru.vikulinva.notificationservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.service.NotificationService;
import ru.vikulinva.notificationservice.time.DateTimeService;

import java.time.Duration;
import java.util.Objects;

/**
 * Ежедневно (по умолчанию 03:00) удаляет уведомления старше TTL (BR-N8).
 * 90 дней — compliance-требование, не «приятная фича».
 */
@Component
public class PurgeOldRecordsScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeOldRecordsScheduler.class);

    private final NotificationService notificationService;
    private final DateTimeService time;
    private final Duration ttl;

    public PurgeOldRecordsScheduler(NotificationService notificationService,
                                       DateTimeService time,
                                       @Value("${notificationservice.purge.ttl-days:90}") int ttlDays) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.time = Objects.requireNonNull(time);
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Scheduled(cron = "${notificationservice.purge.cron:0 0 3 * * *}")
    public void tick() {
        try {
            int removed = notificationService.purgeOlderThan(time.now().minus(ttl));
            if (removed > 0) {
                log.info("Purge: removed {} notifications older than {} days", removed, ttl.toDays());
            }
        } catch (Exception e) {
            log.warn("Purge tick failed: {}", e.getMessage(), e);
        }
    }
}
