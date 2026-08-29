package ru.vikulinva.orderservice.adapter.out.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Конфигурация HTTP-клиента для Catalog Service.
 * Таймауты соответствуют SLA в спеке: 1s connect / 1s read.
 * Resilience4j circuit breaker и retry — через декларативные аннотации
 * на методах {@link ru.vikulinva.orderservice.adapter.out.catalog.client.CatalogRestClient}.
 */
@Configuration
public class CatalogClientConfig {

    @Bean("catalogRestTemplate")
    public RestTemplate catalogRestTemplate(RestTemplateBuilder builder,
                                              @Value("${clients.catalog.base-url}") String baseUrl) {
        // TODO шаг 8: клиент без таймаутов ждёт ответа сколько угодно —
        // и держит поток, пока сосед «думает». SLA соседа описан в спеке.
        return builder
            .rootUri(baseUrl)
            .build();
    }
}
