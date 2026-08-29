package ru.remodov.backoffice.testsupport;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static ru.remodov.backoffice.generated.Tables.IDEMPOTENCY_RECORDS;
import static ru.remodov.backoffice.generated.Tables.MODERATION_ACTIONS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.remodov.backoffice.core.service.DateTimeService;
import ru.remodov.backoffice.core.service.UuidGenerator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BackofficeIntegrationTest {

    @RegisterExtension
    protected static final WireMockExtension CATALOG_WM = WireMockExtension.newInstance()
        .options(wireMockConfig().port(9561))
        .build();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected DSLContext dsl;

    @MockitoBean protected DateTimeService dateTimeService;
    @MockitoBean protected UuidGenerator uuidGenerator;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(MODERATION_ACTIONS).execute();
        dsl.deleteFrom(IDEMPOTENCY_RECORDS).execute();
        CATALOG_WM.resetAll();
    }
}
