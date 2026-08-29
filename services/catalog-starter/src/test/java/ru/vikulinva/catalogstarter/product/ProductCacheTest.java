package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
class ProductCacheTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductService service;

    @MockitoSpyBean
    ProductRepository repository;

    @Autowired
    ObjectProvider<CacheManager> cacheManager;

    private UUID productId;

    @BeforeEach
    void createProduct() {
        cacheManager.ifAvailable(caches ->
            caches.getCacheNames().forEach(name -> caches.getCache(name).clear()));
        productId = service.create("Мышь для кэша", new BigDecimal("1990.00"), 5).getId();
        clearInvocations(repository);
    }

    @Test
    void repeatedRequestIsServedFromCache() throws Exception {
        mvc.perform(get("/products/{id}", productId)).andExpect(status().isOk());
        mvc.perform(get("/products/{id}", productId)).andExpect(status().isOk());
        mvc.perform(get("/products/{id}", productId)).andExpect(status().isOk());

        verify(repository, times(1)).findById(productId);
    }

    @Test
    void priceChangeDropsTheCache() throws Exception {
        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.price", is(1990.00)));

        mvc.perform(patch("/products/{id}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1490.00}"))
            .andExpect(status().isOk());

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.price", is(1490.00)));
    }

    @Test
    void reserveDropsTheCache() throws Exception {
        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.available", is(5)));

        service.reserve(productId, 2);

        mvc.perform(get("/products/{id}", productId))
            .andExpect(jsonPath("$.available", is(3)))
            .andExpect(jsonPath("$.reserved", is(2)));
    }
}
