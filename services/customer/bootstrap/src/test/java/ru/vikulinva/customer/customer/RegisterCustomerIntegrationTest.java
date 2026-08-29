package ru.vikulinva.customer.customer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.http.MediaType;
import ru.vikulinva.customer.BaseIntegrationTest;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;
import ru.vikulinva.customer.testing.CustomerTestObjectGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterCustomerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("AC-1.1: уникальный email → 201 PENDING_VERIFICATION + CustomerRegistered в outbox")
    void register_whenEmailUnique_thenCreatesPendingCustomerAndEmitsEvent() throws Exception {
        BDDMockito.given(customerIdGenerator.generate())
                .willReturn(CustomerId.of(CustomerTestObjectGenerator.CUSTOMER_ID));
        BDDMockito.given(verificationTokenGenerator.generate())
                .willReturn(VerificationTokenValue.of(CustomerTestObjectGenerator.VERIFICATION_TOKEN));

        mockMvc.perform(post("/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "buyer@example.com",
                                  "firstName": "Ivan",
                                  "lastName": "Petrov",
                                  "phone": "+79991234567"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/customers/" + CustomerTestObjectGenerator.CUSTOMER_ID))
                .andExpect(jsonPath("$.id").value(CustomerTestObjectGenerator.CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        assertThat(databasePreparer.fetchCustomerStatus(CustomerTestObjectGenerator.CUSTOMER_ID))
                .isEqualTo(CustomerStatus.PENDING_VERIFICATION);

        assertThat(databasePreparer.hasOutboxEvent("CustomerRegistered", CustomerTestObjectGenerator.CUSTOMER_ID))
                .isTrue();

        String payload = databasePreparer.fetchOutboxPayload(
                "CustomerRegistered", CustomerTestObjectGenerator.CUSTOMER_ID);
        var node = objectMapper.readTree(payload);
        assertThat(node.get("email").asText()).isEqualTo("buyer@example.com");
        assertThat(node.get("verificationToken").asText())
                .isEqualTo(CustomerTestObjectGenerator.VERIFICATION_TOKEN);
    }

    @Test
    @DisplayName("AC-1.2: дубликат email → 409 EMAIL_ALREADY_REGISTERED, ничего не пишется")
    void register_whenEmailExists_thenConflict() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                CustomerTestObjectGenerator.PHONE,
                CustomerStatus.PENDING_VERIFICATION,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(post("/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "buyer@example.com",
                                  "firstName": "Other",
                                  "lastName": "Person"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        assertThat(databasePreparer.hasOutboxEvent("CustomerRegistered", CustomerTestObjectGenerator.CUSTOMER_ID))
                .isFalse();
    }
}
