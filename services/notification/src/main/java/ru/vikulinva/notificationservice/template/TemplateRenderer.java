package ru.vikulinva.notificationservice.template;

import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.generated.tables.pojos.TemplatesPojo;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Простая подстановка {@code ${var}} → значение поверх
 * сгенерённого {@link TemplatesPojo}.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    public Rendered render(TemplatesPojo template, Map<String, String> variables) {
        return new Rendered(
            replace(template.getSubject(), variables),
            replace(template.getBody(), variables));
    }

    private String replace(String text, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = vars.getOrDefault(key, "");
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    public record Rendered(String subject, String body) {}
}
