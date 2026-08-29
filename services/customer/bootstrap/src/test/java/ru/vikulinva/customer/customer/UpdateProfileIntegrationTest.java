package ru.vikulinva.customer.customer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.vikulinva.customer.BaseIntegrationTest;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;
import ru.vikulinva.customer.testing.CustomerTestObjectGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UpdateProfileIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("AC-3.1: свой ACTIVE customer → 200 + CustomerProfileUpdated в outbox")
    void updateProfile_whenSelfActive_thenOkAndEmitsEvent() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.ACTIVE,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(put("/v1/customers/{id}/profile", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.CUSTOMER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "IvanUpdated",
                                  "lastName": "PetrovUpdated",
                                  "phone": "+79990000001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("IvanUpdated"))
                .andExpect(jsonPath("$.lastName").value("PetrovUpdated"))
                .andExpect(jsonPath("$.phone").value("+79990000001"));

        assertThat(databasePreparer.hasOutboxEvent("CustomerProfileUpdated",
                CustomerTestObjectGenerator.CUSTOMER_ID)).isTrue();
    }

    @Test
    @DisplayName("AC-3.2: чужой Customer → 403 FORBIDDEN")
    void updateProfile_whenOtherCustomer_thenForbidden() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.ACTIVE,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(put("/v1/customers/{id}/profile", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.OTHER_CUSTOMER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"X","lastName":"Y"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(databasePreparer.hasOutboxEvent("CustomerProfileUpdated",
                CustomerTestObjectGenerator.CUSTOMER_ID)).isFalse();
    }

    @Test
    @DisplayName("AC-3.3: свой Customer в PENDING_VERIFICATION → 409 PROFILE_UPDATE_FORBIDDEN_STATUS")
    void updateProfile_whenSelfPending_thenConflict() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.PENDING_VERIFICATION,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(put("/v1/customers/{id}/profile", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.CUSTOMER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"X","lastName":"Y"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_UPDATE_FORBIDDEN_STATUS"));
    }
}
