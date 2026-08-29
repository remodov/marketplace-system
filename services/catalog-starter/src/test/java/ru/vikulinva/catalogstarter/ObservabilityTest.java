package ru.vikulinva.catalogstarter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сервис, который нельзя спросить «жив ли ты» и «что у тебя со временем ответа»,
 * в кластер выкатывать нечего. Проверяем то, на что смотрят kubelet и Prometheus.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("проба готовности отвечает — по ней кластер решает, слать ли трафик")
    void readinessProbeIsExposed() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("проба живости отвечает — по ней кластер решает, перезапускать ли под")
    void livenessProbeIsExposed() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("метрики отдаются в формате Prometheus и помечены именем сервиса")
    void prometheusMetricsAreExposed() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk());

        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("http_server_requests")))
            .andExpect(content().string(containsString("application=\"catalog-starter\"")));
    }
}
