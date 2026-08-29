package ru.vikulinva.notificationservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vikulinva.notificationservice.time.DateTimeService;
import ru.vikulinva.notificationservice.time.UuidGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Production-реализации сервисных интерфейсов. См. {@code BS-5/BS-6}
 * из spring-bootstrap-style-guide: интерфейсы в core должны иметь
 * production-бины, иначе context refresh падает на старте.
 */
@Configuration
public class ServiceBeansConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public DateTimeService dateTimeService(Clock clock) {
        return () -> Instant.now(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public UuidGenerator uuidGenerator() {
        return UUID::randomUUID;
    }
}
