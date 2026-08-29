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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductChangeApiTest {

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
        productId = service.create("Беспроводная мышь", new BigDecimal("1990.00"), 5).getId();
    }

    @Test
    void priceIsChanged() throws Exception {
        mvc.perform(patch("/products/{id}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1490.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price", is(1490.00)));

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.price", is(1490.00)));
    }

    @Test
    void negativePriceIsRejectedWithFieldName() throws Exception {
        mvc.perform(patch("/products/{id}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.price", notNullValue()));
    }

    @Test
    void priceOfUnknownProductIsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();

        mvc.perform(patch("/products/{id}/price", missing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":100.00}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail", containsString(missing.toString())));
    }

    @Test
    void restockIncreasesStock() throws Exception {
        mvc.perform(patch("/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":7}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stock", is(12)));
    }

    @Test
    void writeOffBelowZeroIsConflict() throws Exception {
        mvc.perform(patch("/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-9}"))
            .andExpect(status().isConflict());

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.stock", is(5)));
    }

    @Test
    void writeOffCannotTouchReservedGoods() throws Exception {
        mvc.perform(post("/products/{id}/reserve", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":4}"))
            .andExpect(status().isOk());

        mvc.perform(patch("/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-3}"))
            .andExpect(status().isConflict());

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.stock", is(5)))
            .andExpect(jsonPath("$.available", is(1)));
    }

    @Test
    void zeroDeltaIsBadRequest() throws Exception {
        mvc.perform(patch("/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingDeltaIsBadRequest() throws Exception {
        mvc.perform(patch("/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.delta", notNullValue()));
    }
}
