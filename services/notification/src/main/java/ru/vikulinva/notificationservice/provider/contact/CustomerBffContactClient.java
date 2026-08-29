package ru.vikulinva.notificationservice.provider.contact;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.vikulinva.notificationservice.domain.UserContact;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerBffContactClient implements ContactClient {

    private static final String INSTANCE = "customer-bff";
    private static final Logger log = LoggerFactory.getLogger(CustomerBffContactClient.class);

    private final RestTemplate restTemplate;

    public CustomerBffContactClient(@Qualifier("customerBffRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "fallback")
    @Retry(name = INSTANCE)
    public Optional<UserContact> findContact(UUID userId) {
        try {
            ContactResponse response = restTemplate.getForObject(
                "/api/v1/users/{id}/contact", ContactResponse.class, userId);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new UserContact(
                userId,
                response.email,
                response.pushTokens == null ? List.of() : response.pushTokens,
                response.locale == null ? "ru" : response.locale));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private Optional<UserContact> fallback(UUID userId, Throwable t) {
        log.warn("Customer BFF unavailable for {}: {}", userId, t.getMessage());
        throw new ContactLookupFailedException(t);
    }

    public static class ContactResponse {
        public String email;
        public List<String> pushTokens;
        public String locale;
    }

    public static final class ContactLookupFailedException extends RuntimeException {
        public ContactLookupFailedException(Throwable cause) {
            super("Customer BFF unavailable", cause);
        }
    }

    @Configuration
    static class Cfg {

        @Bean("customerBffRestTemplate")
        RestTemplate customerBffRestTemplate(RestTemplateBuilder builder,
                                               @Value("${clients.customer-bff.base-url:http://localhost:9003}") String baseUrl) {
            return builder
                .rootUri(baseUrl)
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofMillis(1000))
                .build();
        }
    }
}
