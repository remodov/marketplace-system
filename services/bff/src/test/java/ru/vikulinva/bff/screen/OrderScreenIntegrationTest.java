package ru.vikulinva.bff.screen;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Экран заказа: клиент делает один запрос, BFF ходит в три сервиса.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderScreenIntegrationTest {

    private static final WireMockServer ORDER = new WireMockServer(wireMockConfig().port(18101));
    private static final WireMockServer CATALOG = new WireMockServer(wireMockConfig().port(18102));
    private static final WireMockServer PAYMENT = new WireMockServer(wireMockConfig().port(18103));

    @Autowired
    MockMvc mvc;

    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeAll
    static void startStubs() {
        ORDER.start();
        CATALOG.start();
        PAYMENT.start();
    }

    @AfterAll
    static void stopStubs() {
        ORDER.stop();
        CATALOG.stop();
        PAYMENT.stop();
    }

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        registry.add("downstream.order", () -> "http://localhost:18101");
        registry.add("downstream.catalog", () -> "http://localhost:18102");
        registry.add("downstream.payment", () -> "http://localhost:18103");
    }

    @BeforeEach
    void stubs() {
        ORDER.resetAll();
        CATALOG.resetAll();
        PAYMENT.resetAll();

        ORDER.stubFor(get(urlPathMatching("/api/v1/orders/.*")).willReturn(okJson("""
            {
              "id": "%s",
              "status": "PAID",
              "total": "3980.00",
              "items": [{ "productId": "%s", "quantity": 2 }]
            }
            """.formatted(orderId, productId))));

        CATALOG.stubFor(get(urlPathMatching("/api/v1/products/.*")).willReturn(okJson("""
            { "id": "%s", "title": "Беспроводная мышь", "price": "1990.00", "currency": "RUB" }
            """.formatted(productId))));

        PAYMENT.stubFor(get(urlPathMatching("/api/v1/payments/by-order/.*")).willReturn(okJson("""
            { "id": "%s", "status": "CAPTURED" }
            """.formatted(UUID.randomUUID()))));
    }

    @Test
    @DisplayName("экран собирается одним запросом клиента из трёх сервисов")
    void screenIsAssembledFromThreeServices() throws Exception {
        mvc.perform(get("/api/v1/screens/order/{id}", orderId).header("X-Client-Id", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PAID")))
            .andExpect(jsonPath("$.paymentStatus", is("CAPTURED")))
            .andExpect(jsonPath("$.items[0].title", is("Беспроводная мышь")))
            .andExpect(jsonPath("$.items[0].quantity", is(2)));

        ORDER.verify(1, getRequestedFor(urlPathMatching("/api/v1/orders/.*")));
        CATALOG.verify(1, getRequestedFor(urlPathMatching("/api/v1/products/.*")));
        PAYMENT.verify(1, getRequestedFor(urlPathMatching("/api/v1/payments/by-order/.*")));
    }

    @Test
    @DisplayName("нет платежа — экран всё равно собирается")
    void screenSurvivesMissingPayment() throws Exception {
        PAYMENT.resetAll();
        PAYMENT.stubFor(get(urlPathMatching("/api/v1/payments/by-order/.*"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.notFound()));

        mvc.perform(get("/api/v1/screens/order/{id}", orderId).header("X-Client-Id", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus", is("NONE")))
            .andExpect(jsonPath("$.items[0].title", is("Беспроводная мышь")));
    }
}
