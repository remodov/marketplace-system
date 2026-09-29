package ru.vikulinva.catalogstarter.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.vikulinva.catalogstarter.search.LlmClient;

import java.util.List;

/**
 * Ответ на вопрос покупателя по документам магазина.
 *
 * <p>Схема целиком: вопрос → поиск похожих кусков → подсказка из найденного →
 * ответ модели. Модель тут ничего не знает про магазин и не должна знать: всё,
 * чем она располагает, лежит в подсказке. Это и есть смысл RAG — знание
 * приносят из базы, а не из весов модели.
 *
 * <p>Главное правило, из-за которого всё и затевалось: <b>ничего не нашли —
 * отвечаем «не знаю», а не зовём модель</b>. Модель, которой не дали контекст,
 * не молчит — она сочиняет правдоподобный ответ про сроки возврата, и по нему
 * покупатель идёт к оператору. Отказ дешевле выдуманного ответа.
 */
@Service
public class RagAssistant {

    private static final Logger log = LoggerFactory.getLogger(RagAssistant.class);

    /* Сколько кусков кладём в подсказку. Больше — дороже и хуже: лишние куски
       разбавляют нужный и уводят ответ в сторону. */
    private static final int TOP_K = 3;

    /* Порог близости. Не угадан, а измерен на этих документах: вопросы по делу
       дают 0.27-0.53, вопрос «как поменять масло в двигателе» — ровно 0.00.
       Со смысловой моделью эмбеддингов порог подбирают заново, глядя на выдачу. */
    private static final double MIN_SCORE = 0.15;

    private static final String PROMPT = """
        Ты помощник интернет-магазина. Ответь на вопрос покупателя, опираясь
        ТОЛЬКО на фрагменты ниже. Если ответа в них нет, так и скажи — не
        придумывай. Отвечай коротко, двумя-тремя предложениями.

        Фрагменты:
        %s

        Вопрос: %s
        """;

    private final KnowledgeBase base;
    private final ObjectProvider<LlmClient> llm;

    public RagAssistant(KnowledgeBase base, ObjectProvider<LlmClient> llm) {
        this.base = base;
        this.llm = llm;
    }

    /** Ответ и куски, по которым он собран: без источников ответу нельзя верить. */
    public record Answer(String text, List<String> sources, boolean grounded) {
    }

    public Answer ask(String question) {
        List<KnowledgeBase.Match> found = base.search(question, TOP_K, MIN_SCORE);
        if (found.isEmpty()) {
            return new Answer("Не нашёл ответа в документах магазина. "
                + "Спросите оператора — он посмотрит вручную.", List.of(), false);
        }

        List<String> sources = found.stream().map(m -> m.chunk().source()).distinct().toList();
        String context = found.stream()
            .map(m -> "- " + m.chunk().text())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        LlmClient client = llm.getIfAvailable();
        if (client == null) {
            /* Без провайдера отдаём найденное как есть. Половина пользы RAG —
               именно в поиске: человек видит нужный абзац, даже когда сформулировать
               ответ некому. */
            return new Answer(found.get(0).chunk().text(), sources, true);
        }
        try {
            return new Answer(client.complete(PROMPT.formatted(context, question)).strip(), sources, true);
        } catch (RuntimeException e) {
            log.warn("Модель недоступна, отдаём найденный фрагмент: {}", e.toString());
            return new Answer(found.get(0).chunk().text(), sources, true);
        }
    }
}
