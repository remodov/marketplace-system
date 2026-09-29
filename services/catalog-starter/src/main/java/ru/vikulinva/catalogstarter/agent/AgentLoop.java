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
    public Result run(String task, boolean confirmed) {
        LlmClient client = llm.getIfAvailable();
        if (client == null) {
            return new Result("Агент недоступен: не настроен провайдер модели.", List.of(), null);
        }

        List<String> trace = new ArrayList<>();
        StringBuilder observations = new StringBuilder();

        for (int step = 1; step <= MAX_STEPS; step++) {
            String raw;
            try {
                raw = client.complete(PROMPT.formatted(tools.catalogue(), task, observations));
            } catch (RuntimeException e) {
                log.warn("Модель недоступна на шаге {}: {}", step, e.toString());
                return new Result("Модель недоступна, попробуйте позже.", List.copyOf(trace), null);
            }

            JsonNode decision = parse(raw);
            if (decision == null) {
                /* Модель ответила не JSON. Не падаем и не гадаем — говорим ей об
                   этом наблюдением: следующий заход обычно получается. */
                trace.add("шаг " + step + ": ответ не разобран, просим повторить");
                observations.append("\nОтвет не разобран. Верни один JSON-объект.");
                continue;
            }

            if (decision.hasNonNull("answer")) {
                trace.add("шаг " + step + ": ответ готов");
                return new Result(decision.get("answer").asText(), List.copyOf(trace), null);
            }

            String name = decision.path("tool").asText("");
            Tool tool = tools.find(name).orElse(null);
            if (tool == null) {
                trace.add("шаг " + step + ": инструмента «" + name + "» нет");
                observations.append("\nИнструмента «").append(name)
                    .append("» не существует. Доступны только перечисленные выше.");
                continue;
            }

            if (tool.mutating() && !confirmed) {
                String call = name + " " + decision.path("args");
                trace.add("шаг " + step + ": нужен человек — " + call);
                return new Result("Нужно подтверждение: агент хочет выполнить " + call,
                    List.copyOf(trace), call);
            }

            String observation = tool.run(decision.path("args"));
            trace.add("шаг " + step + ": " + name + " → " + observation.replace('\n', ';'));
            observations.append("\nРезультат ").append(name).append(": ").append(observation);
        }

        /* Шаги кончились. Честный отказ лучше последнего ответа модели: она
           к этому моменту уже ходит по кругу, и её «ответ» ничем не обоснован. */
        return new Result("Не уложился в " + MAX_STEPS + " шагов. Передаю оператору.",
            List.copyOf(trace), null);
    }

    private JsonNode parse(String answer) {
        try {
            return objectMapper.readTree(answer.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
