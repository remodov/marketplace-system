package ru.vikulinva.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.customer.bootstrap.CustomerApplication;
import ru.vikulinva.customer.core.customer.port.out.CustomerIdGenerator;
import ru.vikulinva.customer.core.customer.port.out.VerificationTokenGenerator;
import ru.vikulinva.customer.testing.CustomerDatabasePreparer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@SpringBootTest(classes = CustomerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5434/customer",
        "spring.datasource.username=customer",
        "spring.datasource.password=customer"
})
public abstract class BaseIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-05-23T10:00:00Z");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected DSLContext dslContext;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CustomerDatabasePreparer databasePreparer;

    @MockitoBean
    protected Clock clock;

    @MockitoBean
    protected CustomerIdGenerator customerIdGenerator;

    @MockitoBean
    protected VerificationTokenGenerator verificationTokenGenerator;

    @BeforeEach
    void resetState() {
        databasePreparer.clearAll();
        BDDMockito.given(clock.instant()).willReturn(FIXED_NOW);
        BDDMockito.given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }
}
