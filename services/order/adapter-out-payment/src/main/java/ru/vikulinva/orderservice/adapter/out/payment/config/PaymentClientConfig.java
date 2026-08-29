package ru.vikulinva.orderservice.adapter.out.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP-клиент Payment Service. Таймауты — 1s connect / 2s read
 * (refund — операция тяжелее catalog-lookup, но всё равно sync).
 */
@Configuration
public class PaymentClientConfig {

    @Bean("paymentRestTemplate")
    public RestTemplate paymentRestTemplate(RestTemplateBuilder builder,
                                              @Value("${clients.payment.base-url}") String baseUrl) {
        return builder
            .rootUri(baseUrl)
            .connectTimeout(Duration.ofMillis(1000))
            .readTimeout(Duration.ofMillis(2000))
            .build();
    }
}
