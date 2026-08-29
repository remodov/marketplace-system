package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductDiscountApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductService service;

    @Autowired
    ProductRepository repository;

    private UUID productId;

    @BeforeEach
    void createProduct() {
        repository.deleteAll();
        productId = service.create("Механическая клавиатура", new BigDecimal("5000.00"), 4).getId();
    }

    @Test
    void discountIsApplied() throws Exception {
        mvc.perform(patch("/products/{id}/discount", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":20}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price", is(4000.00)));
    }

    @Test
    void tooDeepDiscountIsRejectedAndPriceIsUntouched() throws Exception {
        mvc.perform(patch("/products/{id}/discount", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":60}"))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.price", is(5000.00)));
    }
}
