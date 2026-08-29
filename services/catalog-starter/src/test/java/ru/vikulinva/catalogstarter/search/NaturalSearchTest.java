package ru.vikulinva.catalogstarter.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.catalogstarter.product.ProductRepository;
import ru.vikulinva.catalogstarter.product.ProductService;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Поиск по фразе. Модель подменена: настоящий провайдер в тестах не нужен и вреден —
 * он платный, медленный и отвечает каждый раз по-разному.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NaturalSearchTest {

    /** Подменённая модель: считает вызовы и умеет ломаться по команде. */
    static class FakeLlm implements LlmClient {
        final AtomicInteger calls = new AtomicInteger();
        volatile String answer = """
            {"text":"мышь","maxPrice":2000,"inStockOnly":true}
            """;
        volatile boolean broken = false;

        @Override
        public String complete(String prompt) {
            calls.incrementAndGet();
            if (broken) {
                throw new IllegalStateException("провайдер недоступен");
            }
            return answer;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeLlm fakeLlm() {
            return new FakeLlm();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProductService products;
    @Autowired ProductRepository repository;
    @Autowired FakeLlm llm;
    @Autowired CacheManager caches;

    @BeforeEach
    void fillCatalog() {
        repository.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        llm.calls.set(0);
        llm.broken = false;
        llm.answer = """
            {"text":"мышь","maxPrice":2000,"inStockOnly":true}
            """;

        products.create("Беспроводная мышь", new BigDecimal("1990.00"), 5);
        products.create("Мышь игровая премиум", new BigDecimal("7900.00"), 3);
        products.create("Мышь офисная", new BigDecimal("890.00"), 0);
        products.create("Механическая клавиатура", new BigDecimal("5400.00"), 2);
    }

    @Test
    @DisplayName("фраза превращается в фильтры: и по названию, и по цене, и по наличию")
    void phraseBecomesFilters() throws Exception {
        mvc.perform(get("/products/search").param("q", "недорогая беспроводная мышь в наличии"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title", is("Беспроводная мышь")));
    }

    @Test
    @DisplayName("одна и та же фраза не ходит к модели дважды")
    void repeatedPhraseIsCached() throws Exception {
        mvc.perform(get("/products/search").param("q", "мышь подешевле")).andExpect(status().isOk());
        mvc.perform(get("/products/search").param("q", "мышь подешевле")).andExpect(status().isOk());
        mvc.perform(get("/products/search").param("q", "мышь подешевле")).andExpect(status().isOk());

        assertThat(llm.calls.get()).as("походов к модели").isEqualTo(1);
    }

    @Test
    @DisplayName("провайдер лежит — поиск работает как обычный текстовый, а не отдаёт ошибку")
    void brokenProviderFallsBackToPlainSearch() throws Exception {
        llm.broken = true;

        mvc.perform(get("/products/search").param("q", "мышь"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("модель ответила мусором — ищем по исходной фразе, а не падаем")
    void garbageAnswerIsSurvived() throws Exception {
        llm.answer = "конечно! вот ваш ответ: мышь дешевле 2000";

        mvc.perform(get("/products/search").param("q", "клавиатура"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title", is("Механическая клавиатура")));
    }
}
