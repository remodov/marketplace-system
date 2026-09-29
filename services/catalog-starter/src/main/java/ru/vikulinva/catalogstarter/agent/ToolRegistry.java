package ru.vikulinva.catalogstarter.agent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Список инструментов, доступных агенту.
 *
 * <p>Белый список, а не поиск по имени в коде: модель называет инструмент
 * строкой, и строка приходит из её ответа. Всё, чего нет в списке, должно
 * отбиваться на входе — иначе ответ модели превращается в команду серверу.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Tool> all() {
        return List.copyOf(byName.values());
    }

    /** Каталог инструментов строками — он уходит в подсказку модели. */
    public String catalogue() {
        return byName.values().stream()
            .map(t -> "- %s: %s%s".formatted(t.name(), t.description(),
                t.mutating() ? " (меняет данные, нужно подтверждение)" : ""))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("(инструментов нет)");
    }
}
