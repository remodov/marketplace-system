package ru.vikulinva.orderservice.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST-тест через MockMvc: проходит весь HTTP-стек (маппинг
 * URL, JSON-сериализация, JWT-авторизация, ProblemDetails) от запроса до
 * ответа. Покрывает:
 *
 * <ul>
 *   <li>Create → Confirm → Pay → Ship → Deliver через реальные REST-вызовы.</li>
 *   <li>RFC 9457 ProblemDetails при ошибках.</li>
 *   <li>JWT-авторизация (Spring Security с jwt() postProcessor для customer/seller).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class OrderRestE2ETest extends PlatformBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
    }

    @Test
    @DisplayName("E2E: Create → Confirm → Pay → Ship → Deliver через REST")
    void fullPurchasePath() throws Exception {
        var customerId = UUID.randomUUID();
        var sellerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var orderUuid = UUID.randomUUID();
        var orderItemUuid = UUID.randomUUID();
        AtomicInteger uuidCallCount = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            uuidCallCount.getAndIncrement() == 0 ? orderUuid : orderItemUuid);
        given(dateTimeService.now()).willReturn(java.time.Instant.parse("2026-04-01T10:00:00Z"));

        catalog.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "200.00", "currency": "RUB" }
                """.formatted(productId))));

        // 1. Create — JWT с ролью customer
        String createBody = """
            {
              "items": [{
                "productId": "%s",
                "sellerId": "%s",
                "quantity": 1
              }],
              "shippingAddress": {
                "country": "RU", "city": "Moscow", "street": "Tverskaya 1", "postalCode": "125009"
              }
            }
            """.formatted(productId, sellerId);

        String createdJson = mockMvc.perform(post("/api/v1/orders")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer")))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        String orderId = created.get("id").asText();

        // 2. Confirm
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId)
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // 3. Pay (внутренний эндпоинт, без JWT-роли)
        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        // 4. Ship — JWT с ролью seller (sub = sellerId)
        mockMvc.perform(post("/api/v1/orders/{id}/ship", orderId)
                .with(jwt().jwt(j -> j.subject(sellerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_seller")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"TRACK-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SHIPPED"));

        // 5. Deliver — JWT с ролью customer
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELIVERED"));

        // 6. GET — проверяем итоговое состояние
        String fetched = mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(fetched).get("status").asText()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("ошибка домена → ProblemDetails 9457 в application/problem+json")
    void notFound_returnsProblemDetails() throws Exception {
        var customerId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID())
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"))))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("список заказов покупателя возвращает пагинированный JSON")
    void listMyOrders_returnsPage() throws Exception {
        var customerId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
    }
}
