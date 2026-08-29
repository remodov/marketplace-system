package ru.vikulinva.customer.customer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.customer.BaseIntegrationTest;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;
import ru.vikulinva.customer.testing.CustomerTestObjectGenerator;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VerifyEmailIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("AC-2.1: валидный неистёкший token → 200 ACTIVE + CustomerEmailVerified в outbox")
    void verify_whenTokenValid_thenActivatesAndEmitsEvent() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.PENDING_VERIFICATION,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);
        databasePreparer.insertToken(
                CustomerTestObjectGenerator.VERIFICATION_TOKEN,
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW.plus(Duration.ofHours(24)),
                null);

        mockMvc.perform(post("/v1/customers/email-verifications/{token}",
                        CustomerTestObjectGenerator.VERIFICATION_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").value(CustomerTestObjectGenerator.CUSTOMER_ID.toString()));

        assertThat(databasePreparer.fetchCustomerStatus(CustomerTestObjectGenerator.CUSTOMER_ID))
                .isEqualTo(CustomerStatus.ACTIVE);
        assertThat(databasePreparer.hasOutboxEvent("CustomerEmailVerified", CustomerTestObjectGenerator.CUSTOMER_ID))
                .isTrue();
    }

    @Test
    @DisplayName("AC-2.2: несуществующий token → 410 TOKEN_EXPIRED_OR_INVALID")
    void verify_whenTokenUnknown_thenGone() throws Exception {
        mockMvc.perform(post("/v1/customers/email-verifications/{token}",
                        "unknowntoken000000000000"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED_OR_INVALID"));
    }

    @Test
    @DisplayName("AC-2.3: уже использованный token → 410, повторного события нет")
    void verify_whenTokenAlreadyUsed_thenGone() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.ACTIVE,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);
        databasePreparer.insertToken(
                CustomerTestObjectGenerator.VERIFICATION_TOKEN,
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofHours(1)),
                CustomerTestObjectGenerator.FIXED_NOW.plus(Duration.ofHours(23)),
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofMinutes(10)));

        mockMvc.perform(post("/v1/customers/email-verifications/{token}",
                        CustomerTestObjectGenerator.VERIFICATION_TOKEN))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED_OR_INVALID"));

        assertThat(databasePreparer.hasOutboxEvent("CustomerEmailVerified", CustomerTestObjectGenerator.CUSTOMER_ID))
                .isFalse();
    }

    @Test
    @DisplayName("AC-2.4: просроченный token (> 24h) → 410")
    void verify_whenTokenExpired_thenGone() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.PENDING_VERIFICATION,
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofHours(48)),
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofHours(48)));
        databasePreparer.insertToken(
                CustomerTestObjectGenerator.VERIFICATION_TOKEN,
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofHours(48)),
                CustomerTestObjectGenerator.FIXED_NOW.minus(Duration.ofHours(24)),
                null);

        mockMvc.perform(post("/v1/customers/email-verifications/{token}",
                        CustomerTestObjectGenerator.VERIFICATION_TOKEN))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED_OR_INVALID"));

        assertThat(databasePreparer.fetchCustomerStatus(CustomerTestObjectGenerator.CUSTOMER_ID))
                .isEqualTo(CustomerStatus.PENDING_VERIFICATION);
    }
}
