package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductPriceFilterTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductService service;

    @Autowired
    ProductRepository repository;

    @BeforeEach
    void fillCatalog() {
        repository.deleteAll();
        service.create("Механическая клавиатура", new BigDecimal("5400.00"), 2);
        service.create("Коврик для мыши", new BigDecimal("450.00"), 10);
        service.create("Беспроводная мышь", new BigDecimal("1990.00"), 5);
    }

    @Test
    void returnsOnlyProductsWithinPrice() throws Exception {
        mvc.perform(get("/products").param("maxPrice", "2000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void cheapestGoesFirst() throws Exception {
        mvc.perform(get("/products").param("maxPrice", "6000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title", is("Коврик для мыши")))
            .andExpect(jsonPath("$[2].title", is("Механическая клавиатура")));
    }

    @Test
    void boundaryPriceIsIncluded() throws Exception {
        mvc.perform(get("/products").param("maxPrice", "1990.00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void withoutFilterWholeCatalogIsReturned() throws Exception {
        mvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }
}
