package ru.vikulinva.orderservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.service.UuidGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Production-реализации {@link DateTimeService} и {@link UuidGenerator}.
 * В тестах эти бины подменяются через {@code @MockitoBean} (TS-7), поэтому
 * аннотация {@link ConditionalOnMissingBean} не нужна — Spring сам отдаст
 * приоритет mock-бину при overriding=true. Здесь просто регистрируем
 * нормальные реализации, чтобы сервис стартовал в production/local.
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
