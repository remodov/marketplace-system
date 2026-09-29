package ru.vikulinva.catalogstarter.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.vikulinva.catalogstarter.product.ProductRepository;
import ru.vikulinva.catalogstarter.product.ProductService;
import ru.vikulinva.catalogstarter.search.LlmClient;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Помощник по документам магазина. Провайдер подменён: в тестах он не нужен —
 * платный, медленный и отвечает каждый раз по-разному.
 *
 * <p>Главное, что здесь проверяется, — не «модель ответила», а то, ЧТО ей
 * положили в подсказку и когда её вообще не позвали.
 */
@SpringBootTest
class RagAssistantTest {

    /** Подменённая модель: запоминает последнюю подсказку и считает вызовы. */
    static class FakeLlm implements LlmClient {
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastPrompt = "";

        @Override
        public String complete(String prompt) {
            calls.incrementAndGet();
            lastPrompt = prompt;
            return "Ответ по документам магазина.";
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeLlm fakeLlm() {
            return new FakeLlm();
        }
    }

    @Autowired RagAssistant assistant;
    @Autowired ShopKnowledge knowledge;
    @Autowired KnowledgeBase base;
    @Autowired ProductService products;
    @Autowired ProductRepository repository;
    @Autowired FakeLlm llm;

    @BeforeEach
    void fillShop() {
        repository.deleteAll();
        products.create("Беспроводная мышь", new BigDecimal("1990.00"), 5);
        products.create("Механическая клавиатура", new BigDecimal("5400.00"), 2);
        knowledge.reindex();
        llm.calls.set(0);
        llm.lastPrompt = "";
    }

    @Test
    @DisplayName("база собирается из правил магазина и карточек товаров")
    void knowledgeHoldsDocumentsAndProducts() {
        assertThat(base.size()).isGreaterThan(5);

        assertThat(assistant.ask("сколько стоит доставка курьером").sources())
            .contains("delivery.md");
        assertThat(assistant.ask("есть ли беспроводная мышь и сколько стоит").sources())
            .contains("каталог: Беспроводная мышь");
    }

    /* Ради этого RAG и делают: в подсказке лежит текст из документа, и ответ
       модели опирается на него, а не на то, что она помнит про магазины вообще. */
    @Test
    @DisplayName("найденный фрагмент уезжает в подсказку модели")
    void foundFragmentGoesIntoThePrompt() {
        assistant.ask("сколько дней на возврат товара");

        assertThat(llm.calls.get()).isEqualTo(1);
        assertThat(llm.lastPrompt).contains("14 дней");
        assertThat(llm.lastPrompt).contains("сколько дней на возврат товара");
        assertThat(llm.lastPrompt).contains("Если ответа в них нет, так и скажи");
    }

    /* Самая дорогая ошибка RAG: ничего не нашли, но всё равно спросили модель.
       Она не скажет «не знаю» — она сочинит срок возврата, и по нему пойдут в суд. */
    @Test
    @DisplayName("ничего не нашли — отвечаем «не знаю» и к модели не идём")
    void nothingFoundMeansHonestRefusal() {
        RagAssistant.Answer answer = assistant.ask("как поменять масло в двигателе автомобиля");

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.text()).contains("Не нашёл ответа");
        assertThat(answer.sources()).isEmpty();
        assertThat(llm.calls.get()).as("походов к модели").isZero();
    }

    @Test
    @DisplayName("у ответа всегда видно источник")
    void answerCarriesItsSources() {
        RagAssistant.Answer answer = assistant.ask("когда вернут деньги за возврат");

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.sources()).isNotEmpty();
        assertThat(answer.text()).isEqualTo("Ответ по документам магазина.");
    }
}
