package ru.vikulinva.catalogstarter.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Превращает фразу покупателя в фильтры каталога.
 *
 * <p>Три вещи, без которых это нельзя выпускать в прод: ответ модели кэшируется
 * (одна и та же фраза повторяется чаще, чем кажется), провайдер может лежать —
 * тогда поиск обязан работать как обычный текстовый, и ответ модели разбирается
 * оборонительно: она вернёт не то, что обещала, ровно тогда, когда этого не ждут.
 */
@Component
public class QueryUnderstanding {

    private static final Logger log = LoggerFactory.getLogger(QueryUnderstanding.class);

    private static final String PROMPT = """
        Разбери запрос покупателя интернет-магазина в JSON без пояснений.
        Поля: text — что искать по названию, maxPrice — потолок цены в рублях или null,
        inStockOnly — true, если покупателю нужен товар в наличии.
        Запрос: %s
        """;

    private final ObjectProvider<LlmClient> llm;
    private final ObjectMapper objectMapper;

    public QueryUnderstanding(ObjectProvider<LlmClient> llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "nl-queries", key = "#query")
    public SearchFilters understand(String query) {
        LlmClient client = llm.getIfAvailable();
        if (client == null) {
            return SearchFilters.plainText(query);
        }
        try {
            return parse(client.complete(PROMPT.formatted(query)), query);
        } catch (RuntimeException e) {
            log.warn("Разбор запроса не удался, ищем как есть: {}", e.toString());
            return SearchFilters.plainText(query);
        }
    }

    private SearchFilters parse(String answer, String original) {
        try {
            JsonNode node = objectMapper.readTree(answer);
            String text = node.hasNonNull("text") ? node.get("text").asText() : original;
            BigDecimal maxPrice = node.hasNonNull("maxPrice") ? node.get("maxPrice").decimalValue() : null;
            boolean inStockOnly = node.hasNonNull("inStockOnly") && node.get("inStockOnly").asBoolean();
            return new SearchFilters(text.isBlank() ? original : text, maxPrice, inStockOnly);
        } catch (Exception e) {
            log.warn("Модель вернула не JSON, ищем как есть");
            return SearchFilters.plainText(original);
        }
    }
}
