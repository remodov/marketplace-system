package ru.vikulinva.orderservice.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.orderservice.testutil.base.PlatformBaseIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;

/**
 * Один и тот же запрос, посланный дважды, должен создать один заказ.
 * Кнопка «Оплатить» нажимается дважды чаще, чем кажется: покупатель нервничает,
 * сеть моргает, мобильный клиент повторяет отправку сам.
 */
@AutoConfigureMockMvc
class IdempotencyIntegrationTest extends PlatformBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        databasePreparer.clearAll().prepare();
        catalog.resetAll();
        given(uuidGenerator.generate()).willAnswer(inv -> UUID.randomUUID());
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        catalog.stubFor(get(urlPathMatching("/api/v1/products/.*"))
            .willReturn(okJson("""
                { "id": "%s", "price": "200.00", "currency": "RUB" }
                """.formatted(productId))));
    }

    private String body(int quantity) {
        return """
            {
              "items": [{ "productId": "%s", "sellerId": "%s", "quantity": %d }],
              "shippingAddress": {
                "country": "RU", "city": "Moscow", "street": "Tverskaya 1", "postalCode": "125009"
              }
            }
            """.formatted(productId, sellerId, quantity);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createOrder(
            String idempotencyKey, int quantity) {
        return post("/api/v1/orders")
            .with(jwt().jwt(j -> j.subject(customerId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_customer")))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(quantity));
    }

    @Test
    @DisplayName("повтор с тем же ключом и тем же телом отдаёт прежний заказ, а не создаёт второй")
    void sameKeySameBody_returnsSameOrder() throws Exception {
        String key = UUID.randomUUID().toString();

        String firstJson = mockMvc.perform(createOrder(key, 1))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String secondJson = mockMvc.perform(createOrder(key, 1))
            .andExpect(status().is2xxSuccessful())
            .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(firstJson);
        JsonNode second = objectMapper.readTree(secondJson);

        assertThat(second.get("id").asText())
            .as("повтор должен вернуть тот же заказ")
            .isEqualTo(first.get("id").asText());
        assertThat(dsl.fetchCount(ORDERS)).as("заказ должен быть ровно один").isEqualTo(1);
    }

    @Test
    @DisplayName("тот же ключ с другим телом — это ошибка клиента, а не молчаливая подмена")
    void sameKeyDifferentBody_isConflict() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(createOrder(key, 1)).andExpect(status().isCreated());

        mockMvc.perform(createOrder(key, 5))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(dsl.fetchCount(ORDERS)).isEqualTo(1);
    }

    @Test
    @DisplayName("разные ключи — разные заказы")
    void differentKeys_createDifferentOrders() throws Exception {
        mockMvc.perform(createOrder(UUID.randomUUID().toString(), 1)).andExpect(status().isCreated());
        mockMvc.perform(createOrder(UUID.randomUUID().toString(), 1)).andExpect(status().isCreated());

        assertThat(dsl.fetchCount(ORDERS)).isEqualTo(2);
    }
}
