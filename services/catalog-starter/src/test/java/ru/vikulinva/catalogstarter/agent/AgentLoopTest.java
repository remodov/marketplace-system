package ru.vikulinva.catalogstarter.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.vikulinva.catalogstarter.product.Product;
import ru.vikulinva.catalogstarter.product.ProductRepository;
import ru.vikulinva.catalogstarter.product.ProductService;
import ru.vikulinva.catalogstarter.search.LlmClient;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Цикл агента. Модель подменена сценарием: в тестах агента проверяют не
 * сообразительность модели, а поведение цикла вокруг неё — что он делает с
 * неизвестным инструментом, с мусором вместо JSON, с бесконечным хождением по
 * кругу и с опасным вызовом.
 */
@SpringBootTest
class AgentLoopTest {

    /** Модель по сценарию: отдаёт заготовленные ответы по очереди. */
    static class ScriptedLlm implements LlmClient {
        final Deque<String> script = new ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile String always = null;

        @Override
        public String complete(String prompt) {
            calls.incrementAndGet();
            if (always != null) {
                return always;
            }
            return script.isEmpty() ? "{\"answer\":\"сценарий кончился\"}" : script.removeFirst();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ScriptedLlm scriptedLlm() {
            return new ScriptedLlm();
        }
    }

    @Autowired AgentLoop agent;
    @Autowired ToolRegistry tools;
    @Autowired ScriptedLlm llm;
    @Autowired ProductService products;
    @Autowired ProductRepository repository;

    private Product мышь;

    @BeforeEach
    void fillCatalog() {
        repository.deleteAll();
        мышь = products.create("Беспроводная мышь", new BigDecimal("1990.00"), 5);
        products.create("Механическая клавиатура", new BigDecimal("5400.00"), 2);
        llm.script.clear();
        llm.always = null;
        llm.calls.set(0);
    }

    /* Вот ради чего цикл: модель не знает остатков, она их СПРАШИВАЕТ, получает
       ответ и отвечает уже по нему. Один вызов модели так не умеет. */
    @Test
    @DisplayName("модель зовёт инструмент, видит результат и отвечает по нему")
    void toolResultComesBackToTheModel() {
        llm.script.add("{\"tool\":\"find_products\",\"args\":{\"query\":\"мышь\"}}");
        llm.script.add("{\"answer\":\"Беспроводная мышь есть, 1990 рублей\"}");

        AgentLoop.Result result = agent.run("есть ли беспроводная мышь");

        assertThat(result.text()).contains("1990");
        assertThat(result.trace()).hasSize(2);
        assertThat(result.trace().get(0)).contains("find_products").contains("Беспроводная мышь");
        assertThat(llm.calls.get()).isEqualTo(2);
    }

    /* Имя инструмента приходит из ответа модели, то есть из текста. Всё, чего
       нет в белом списке, обязано отбиваться — и не падением сервиса. */
    @Test
    @DisplayName("выдуманный инструмент не вызывается, а цикл продолжается")
    void unknownToolIsRejectedNotExecuted() {
        llm.script.add("{\"tool\":\"drop_database\",\"args\":{}}");
        llm.script.add("{\"answer\":\"понял, так нельзя\"}");

        AgentLoop.Result result = agent.run("удали базу");

        assertThat(result.text()).isEqualTo("понял, так нельзя");
        assertThat(result.trace().get(0)).contains("drop_database").contains("нет");
    }

    @Test
    @DisplayName("модель ответила не JSON — просим повторить, а не падаем")
    void garbageAnswerIsSurvived() {
        llm.script.add("Конечно! Сейчас посмотрю каталог.");
        llm.script.add("{\"answer\":\"вот ответ\"}");

        AgentLoop.Result result = agent.run("есть ли мышь");

        assertThat(result.text()).isEqualTo("вот ответ");
        assertThat(result.trace().get(0)).contains("не разобран");
    }

    /* Без потолка модель ходит по кругу за деньги заказчика, а запрос висит. */
    @Test
    @DisplayName("шаги кончились — честный отказ, а не бесконечный цикл")
    void loopStopsAtTheStepLimit() {
        llm.always = "{\"tool\":\"find_products\",\"args\":{\"query\":\"мышь\"}}";

        AgentLoop.Result result = agent.run("найди что-нибудь");

        assertThat(result.text()).contains("Не уложился");
        assertThat(result.trace()).hasSize(AgentLoop.MAX_STEPS);
        assertThat(llm.calls.get()).isEqualTo(AgentLoop.MAX_STEPS);
    }

    /* Для модели «покажи остаток» и «зарезервируй сто штук» одинаково полезны.
       Различает их не подсказка, а флаг у инструмента и остановка цикла. */
    @Test
    @DisplayName("изменяющий вызов ждёт человека и без него ничего не меняет")
    void mutatingToolNeedsConfirmation() {
        llm.always = "{\"tool\":\"reserve\",\"args\":{\"id\":\"" + мышь.getId() + "\",\"quantity\":3}}";

        AgentLoop.Result result = agent.run("зарезервируй три мыши");

        assertThat(result.awaitingConfirm()).contains("reserve");
        assertThat(result.text()).contains("Нужно подтверждение");
        assertThat(products.byId(мышь.getId()).available())
            .as("остаток не тронут без подтверждения").isEqualTo(5);
    }

    @Test
    @DisplayName("человек подтвердил — вызов выполняется")
    void confirmedMutatingToolRuns() {
        llm.script.add("{\"tool\":\"reserve\",\"args\":{\"id\":\"" + мышь.getId() + "\",\"quantity\":3}}");
        llm.script.add("{\"answer\":\"зарезервировал\"}");

        AgentLoop.Result result = agent.run("зарезервируй три мыши", true);

        assertThat(result.awaitingConfirm()).isNull();
        assertThat(result.text()).isEqualTo("зарезервировал");
        assertThat(products.byId(мышь.getId()).available()).isEqualTo(2);
    }

    @Test
    @DisplayName("в каталоге инструментов видно, какие из них опасные")
    void catalogueMarksMutatingTools() {
        List<String> lines = List.of(tools.catalogue().split("\n"));

        assertThat(lines).anySatisfy(line ->
            assertThat(line).contains("reserve").contains("нужно подтверждение"));
        assertThat(lines).anySatisfy(line ->
            assertThat(line).contains("find_products").doesNotContain("подтверждение"));
    }
}
