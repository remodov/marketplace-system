package ru.vikulinva.customer.bootstrap.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ServiceBeansConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
