package ru.vikulinva.catalogstarter.product;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ProductService service;

    @Test
    void createdProductIsReturnedById() throws Exception {
        String body = json.writeValueAsString(
            new ProductController.CreateProductRequest("Беспроводная мышь", new BigDecimal("1990.00"), 7));

        String created = mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title", is("Беспроводная мышь")))
            .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(json.readTree(created).get("id").asText());

        mvc.perform(get("/products/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stock", is(7)));
    }

    @Test
    void searchFindsByPartOfTitle() throws Exception {
        service.create("Механическая клавиатура", new BigDecimal("5400.00"), 3);

        mvc.perform(get("/products").param("query", "клавиат"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title", is("Механическая клавиатура")));
    }

    @Test
    void reserveHoldsStockInsteadOfWritingItOff() throws Exception {
        Product product = service.create("USB-хаб", new BigDecimal("890.00"), 5);
        String body = json.writeValueAsString(new ProductController.ReserveRequest(2));

        mvc.perform(post("/products/{id}/reserve", product.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stock", is(5)))
            .andExpect(jsonPath("$.reserved", is(2)))
            .andExpect(jsonPath("$.available", is(3)));
    }

    @Test
    void reserveMoreThanStockIsRejected() throws Exception {
        Product product = service.create("Коврик", new BigDecimal("450.00"), 1);
        String body = json.writeValueAsString(new ProductController.ReserveRequest(4));

        mvc.perform(post("/products/{id}/reserve", product.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void unknownProductGivesNotFound() throws Exception {
        mvc.perform(get("/products/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }
}
