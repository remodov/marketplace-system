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

    // TODO Б2: превратить фразу в фильтры.
    // Три вещи обязательны, иначе это нельзя выпускать: одна и та же фраза не
    // должна ходить к модели дважды; лежащий провайдер не должен ломать поиск;
    // ответ модели разбирается оборонительно — она вернёт не то, что обещала,
    // ровно тогда, когда этого не ждут.
    public SearchFilters understand(String query) {
        return SearchFilters.plainText(query);
    }

    // TODO Б2: разбор ответа модели. Поля описаны в промпте — но обещание модели
    // не гарантия: она отвечает текстом, а не типом.
    private SearchFilters parse(String answer, String original) {
        return SearchFilters.plainText(original);
    }
}
