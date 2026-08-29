package ru.remodov.catalog.testsupport;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.core.service.UuidGenerator;

@Import(TestJwtConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PlatformBaseIntegrationTest {

    @MockitoBean
    protected DateTimeService dateTimeService;

    @MockitoBean
    protected UuidGenerator uuidGenerator;
}
