package ru.remodov.backoffice.catalog;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("catalog")
@RequiredArgsConstructor
@Slf4j
public class CatalogHealthIndicator implements HealthIndicator {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String PROBE_PATH = "/actuator/health";

    @Qualifier("catalogRestClient")
    private final RestClient catalogRestClient;
    private final CatalogClientSettings settings;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    @Override
    public Health health() {
        Cached cached = cache.get();
        if (cached != null && !cached.isExpired()) {
            return cached.health;
        }
        Health probed = probe();
        cache.set(new Cached(probed, Instant.now()));
        return probed;
    }

    private Health probe() {
        try {
            catalogRestClient.get()
                .uri(URI.create(settings.baseUrl() + PROBE_PATH))
                .retrieve()
                .toBodilessEntity();
            return Health.up().withDetail("baseUrl", settings.baseUrl()).build();
        } catch (Exception e) {
            log.debug("Catalog health probe failed", e);
            return Health.down(e).withDetail("baseUrl", settings.baseUrl()).build();
        }
    }

    private record Cached(Health health, Instant probedAt) {
        boolean isExpired() {
            return Duration.between(probedAt, Instant.now()).compareTo(CACHE_TTL) > 0;
        }
    }
}
