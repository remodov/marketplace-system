package ru.vikulinva.customer.customer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.customer.BaseIntegrationTest;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;
import ru.vikulinva.customer.testing.CustomerTestObjectGenerator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetCustomerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("AC-4.1: свой Customer → 200 + DTO")
    void get_whenSelf_thenOk() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                CustomerTestObjectGenerator.PHONE,
                CustomerStatus.ACTIVE,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(get("/v1/customers/{id}", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.CUSTOMER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CustomerTestObjectGenerator.CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.email").value(CustomerTestObjectGenerator.EMAIL))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("AC-4.3: несуществующий Customer → 404 NOT_FOUND")
    void get_whenNotExists_thenNotFound() throws Exception {
        mockMvc.perform(get("/v1/customers/{id}", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.CUSTOMER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-4.4: чужой Customer (Buyer-роль) → 403 FORBIDDEN")
    void get_whenOtherCustomer_thenForbidden() throws Exception {
        databasePreparer.insertCustomer(
                CustomerTestObjectGenerator.CUSTOMER_ID,
                CustomerTestObjectGenerator.EMAIL,
                CustomerTestObjectGenerator.FIRST_NAME,
                CustomerTestObjectGenerator.LAST_NAME,
                null,
                CustomerStatus.ACTIVE,
                CustomerTestObjectGenerator.FIXED_NOW,
                CustomerTestObjectGenerator.FIXED_NOW);

        mockMvc.perform(get("/v1/customers/{id}", CustomerTestObjectGenerator.CUSTOMER_ID)
                        .with(jwt().jwt(j -> j.subject(CustomerTestObjectGenerator.OTHER_CUSTOMER_ID.toString()))))
                .andExpect(status().isForbidden());
    }
}
