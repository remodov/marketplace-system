package ru.vikulinva.payment.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbc.update("delete from payments");
    }

    private String authorize(UUID order) throws Exception {
        String body = """
            {"orderId":"%s","amount":1990.00,"currency":"RUB"}
            """.formatted(order);
        String json = mvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("платёж создаётся в статусе AUTHORIZED")
    void authorizeCreatesPayment() throws Exception {
        String id = authorize(orderId);

        mvc.perform(get("/api/v1/payments/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("AUTHORIZED")))
            .andExpect(jsonPath("$.orderId", is(orderId.toString())));
    }

    @Test
    @DisplayName("повторная авторизация того же заказа не создаёт второй платёж")
    void authorizeIsIdempotentPerOrder() throws Exception {
        String first = authorize(orderId);
        String second = authorize(orderId);

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        Integer count = jdbc.queryForObject("select count(*) from payments", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("списание переводит платёж в CAPTURED")
    void captureMovesToCaptured() throws Exception {
        String id = authorize(orderId);

        mvc.perform(post("/api/v1/payments/{id}/capture", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("CAPTURED")));
    }

    @Test
    @DisplayName("возврат после списания разрешён")
    void refundAfterCaptureIsAllowed() throws Exception {
        String id = authorize(orderId);
        mvc.perform(post("/api/v1/payments/{id}/capture", id)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/payments/{id}/refund", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("REFUNDED")));
    }

    @Test
    @DisplayName("повторный возврат безопасен: тот же статус, а не ошибка")
    void refundIsIdempotent() throws Exception {
        String id = authorize(orderId);
        mvc.perform(post("/api/v1/payments/{id}/refund", id)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/payments/{id}/refund", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("REFUNDED")));
    }

    @Test
    @DisplayName("списать возвращённый платёж нельзя — 409, а не молчаливая порча данных")
    void captureAfterRefundIsRejected() throws Exception {
        String id = authorize(orderId);
        mvc.perform(post("/api/v1/payments/{id}/refund", id)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/payments/{id}/capture", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("INVALID_PAYMENT_TRANSITION")));

        mvc.perform(get("/api/v1/payments/{id}", id))
            .andExpect(jsonPath("$.status", is("REFUNDED")));
    }

    @Test
    @DisplayName("неизвестный платёж — 404")
    void unknownPaymentIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("нулевая сумма не принимается")
    void zeroAmountIsRejected() throws Exception {
        mvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"%s","amount":0,"currency":"RUB"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }
}
