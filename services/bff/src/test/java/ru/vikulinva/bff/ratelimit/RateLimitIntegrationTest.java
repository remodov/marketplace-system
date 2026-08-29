package ru.vikulinva.bff.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Лимит частоты. Счётчик в Redis из стенда: подними его перед прогоном.
 * Лимит в тестовом профиле — три запроса в минуту.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("четвёртый запрос за минуту получает 429 и Retry-After")
    void fourthRequestIsRejected() throws Exception {
        String client = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/screens/order/{id}", UUID.randomUUID()).header("X-Client-Id", client))
                .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        mvc.perform(get("/api/v1/screens/order/{id}", UUID.randomUUID()).header("X-Client-Id", client))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("лимит считается по клиенту: сосед не расходует чужую квоту")
    void limitIsPerClient() throws Exception {
        String noisy = UUID.randomUUID().toString();
        String quiet = UUID.randomUUID().toString();

        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/v1/screens/order/{id}", UUID.randomUUID()).header("X-Client-Id", noisy));
        }

        mvc.perform(get("/api/v1/screens/order/{id}", UUID.randomUUID()).header("X-Client-Id", quiet))
            .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
}
