package ru.vikulinva.notificationservice.base;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.vikulinva.notificationservice.time.DateTimeService;
import ru.vikulinva.notificationservice.time.UuidGenerator;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Базовый интеграционный тест: реальный Postgres из docker-compose,
 * WireMock для Customer BFF / Mailgun / FCM, мокованные DateTimeService
 * и UuidGenerator для детерминизма (как в order-service).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PlatformBaseIntegrationTest {

    private static final int CUSTOMER_BFF_PORT = 18091;
    private static final int MAILGUN_PORT = 18092;
    private static final int FCM_PORT = 18093;

    // Instance-fields, не static — JUnit5 запустит/остановит WireMock на каждый
    // тестовый метод. Альтернатива (static) не работает корректно при наследовании
    // base-класса несколькими тестовыми классами: server останавливается на
    // afterAll первого класса и второй пытается коннектиться к мёртвому порту.
    @RegisterExtension
    protected final WireMockExtension customerBff = WireMockExtension.newInstance()
        .options(wireMockConfig().port(CUSTOMER_BFF_PORT))
        .build();

    @RegisterExtension
    protected final WireMockExtension mailgun = WireMockExtension.newInstance()
        .options(wireMockConfig().port(MAILGUN_PORT))
        .build();

    @RegisterExtension
    protected final WireMockExtension fcm = WireMockExtension.newInstance()
        .options(wireMockConfig().port(FCM_PORT))
        .build();

    @MockitoBean
    protected DateTimeService dateTimeService;

    @MockitoBean
    protected UuidGenerator uuidGenerator;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        if (databaseCleaner == null) {
            databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        }
        databaseCleaner.clearAll();
        customerBff.resetAll();
        mailgun.resetAll();
        fcm.resetAll();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        // Postgres из docker-compose (порт 5433 по yml)
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/notifications");
        registry.add("spring.datasource.username", () -> "notifications");
        registry.add("spring.datasource.password", () -> "notifications");
        registry.add("clients.customer-bff.base-url", () -> "http://localhost:" + CUSTOMER_BFF_PORT);
        registry.add("notificationservice.mailgun.base-url", () -> "http://localhost:" + MAILGUN_PORT);
        registry.add("notificationservice.fcm.base-url", () -> "http://localhost:" + FCM_PORT);
    }
}
