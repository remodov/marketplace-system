package ru.vikulinva.catalogstarter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.vikulinva.catalogstarter.search.LlmClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Цикл агента: модель решает, что делать, сервис делает и возвращает результат,
 * модель решает снова — и так, пока не появится ответ или не кончатся шаги.
 *
 * <p>Ровно этим агент отличается от одного вызова модели. Вызов даёт текст; цикл
 * даёт действие, результат действия и решение с учётом результата. Всё остальное
 * — обвязка вокруг четырёх строк: <em>спросить → выполнить → дописать
 * наблюдение → спросить снова</em>.
 *
 * <p>Три вещи, без которых цикл нельзя выпускать наружу:
 * <ol>
 *   <li><b>Предел шагов.</b> Модель умеет ходить по кругу: вызвала поиск, не
 *       поняла ответ, вызвала снова. Без потолка это бесконечные деньги и
 *       висящий запрос.</li>
 *   <li><b>Белый список инструментов.</b> Имя инструмента приходит из ответа
 *       модели, то есть из текста, на который влияет пользователь.</li>
 *   <li><b>Подтверждение на изменяющие действия.</b> Прочитать остаток и
 *       зарезервировать сто штук для модели одинаково «полезно».</li>
 * </ol>
 */
@Service
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    static final int MAX_STEPS = 5;

    private static final String PROMPT = """
        Ты агент службы поддержки интернет-магазина. Тебе доступны инструменты:
        %s

        Отвечай ТОЛЬКО одним JSON-объектом, без пояснений вокруг.
        Чтобы вызвать инструмент: {"tool": "имя", "args": {...}}
        Чтобы ответить покупателю: {"answer": "текст"}

        Задача: %s
        %s
        """;

    private final ToolRegistry tools;
    private final ObjectProvider<LlmClient> llm;
    private final ObjectMapper objectMapper;

    public AgentLoop(ToolRegistry tools, ObjectProvider<LlmClient> llm, ObjectMapper objectMapper) {
        this.tools = tools;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    /**
     * Чем кончился цикл.
     *
     * @param text            ответ покупателю или объяснение, почему его нет
     * @param trace           что агент делал по шагам — без этого разбирать нечего
     * @param awaitingConfirm вызов, который ждёт подтверждения человеком, или null
     */
    public record Result(String text, List<String> trace, String awaitingConfirm) {
    }

    public Result run(String task) {
        return run(task, false);
    }

    /**
     * @param confirmed человек уже разрешил изменяющие вызовы в этом прогоне
     */
    // TODO Б4: цикл агента.
    // Шаг цикла: спросить модель подсказкой PROMPT (каталог инструментов, задача
    // и накопленные наблюдения), разобрать ответ, сделать одно из четырёх.
    //   {"answer": "..."}      — вернуть ответ покупателю;
    //   {"tool": "имя", ...}   — найти инструмент в реестре и выполнить, результат
    //                            дописать в наблюдения и пойти на следующий шаг;
    //   инструмента нет        — сказать об этом наблюдением, не падать;
    //   ответ не JSON          — попросить повторить, не гадать.
    // Изменяющий инструмент (tool.mutating()) без confirmed НЕ выполняется:
    // цикл останавливается и возвращает вызов в awaitingConfirm.
    // Шагов не больше MAX_STEPS, и на исходе — честный отказ, а не последний
    // ответ модели: к этому моменту она уже ходит по кругу.
    // Каждый шаг дописывать в trace: без него разбирать поведение агента нечем.
    public Result run(String task, boolean confirmed) {
        return new Result("", List.of(), null);
    }

    private JsonNode parse(String answer) {
        try {
            return objectMapper.readTree(answer.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
