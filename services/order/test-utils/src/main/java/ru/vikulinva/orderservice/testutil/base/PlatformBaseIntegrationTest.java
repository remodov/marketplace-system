package ru.vikulinva.orderservice.testutil.base;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.service.UuidGenerator;
import ru.vikulinva.orderservice.testutil.preparer.OrderDatabasePreparer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Платформенный {@code BaseIntegrationTest} для Order Service.
 *
 * <p>Использует уже работающий Postgres из {@code docker-compose.yml}
 * (упрощение для local dev). На CI планируется заменить на Testcontainers,
 * как только наладим стабильное обнаружение Docker-сокета на Mac.
 *
 * <ul>
 *   <li>Liquibase сам накатит схему при старте контекста (TS-10).</li>
 *   <li>{@code @MockitoBean DateTimeService / UuidGenerator} — детерминизм времени и UUID (TS-7).</li>
 *   <li>WireMock для Catalog поднимается на динамическом порту (TS-6).</li>
 *   <li>Профиль {@code integration-test} → выключенный SecurityFilter, no Kafka, no Redis (TS-9, TS-10).</li>
 * </ul>
 *
 * <p>Перед запуском: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PlatformBaseIntegrationTest {

    /** Фиксированный порт WireMock — упрощает {@code @DynamicPropertySource}. */
    private static final int CATALOG_WIREMOCK_PORT = 18089;
    private static final int PAYMENT_WIREMOCK_PORT = 18090;

    @RegisterExtension
    protected static final WireMockExtension catalog = WireMockExtension.newInstance()
        .options(wireMockConfig().port(CATALOG_WIREMOCK_PORT))
        .build();

    @RegisterExtension
    protected static final WireMockExtension payment = WireMockExtension.newInstance()
        .options(wireMockConfig().port(PAYMENT_WIREMOCK_PORT))
        .build();

    @MockitoBean
    protected DateTimeService dateTimeService;

    @MockitoBean
    protected UuidGenerator uuidGenerator;

    @Autowired
    protected OrderDatabasePreparer databasePreparer;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        // Подключаемся к Postgres из docker-compose.yml — тот же, что и для local dev.
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/orders");
        registry.add("spring.datasource.username", () -> "orders");
        registry.add("spring.datasource.password", () -> "orders");
        registry.add("clients.catalog.base-url", () -> "http://localhost:" + CATALOG_WIREMOCK_PORT);
        registry.add("clients.payment.base-url", () -> "http://localhost:" + PAYMENT_WIREMOCK_PORT);
    }
}
