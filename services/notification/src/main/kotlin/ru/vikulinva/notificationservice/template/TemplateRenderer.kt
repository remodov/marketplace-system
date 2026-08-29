package ru.vikulinva.notificationservice.template

import org.springframework.stereotype.Component
import ru.vikulinva.notificationservice.generated.tables.pojos.TemplatesPojo

/**
 * Подстановка `${var}` → значение.
 *
 * Тот же код, что был на Java, только язык другой — и разница видна построчно:
 * data-класс вместо record, `?:` вместо проверок на null, шаблон разбирается
 * одним `replace` с лямбдой вместо ручного цикла по Matcher.
 */
@Component
class TemplateRenderer {

    data class Rendered(val subject: String, val body: String)

    fun render(template: TemplatesPojo, variables: Map<String, String>): Rendered = Rendered(
        subject = template.subject.substitute(variables),
        body = template.body.substitute(variables),
    )

    private fun String?.substitute(variables: Map<String, String>): String =
        this?.let { text ->
            PLACEHOLDER.replace(text) { match -> variables[match.groupValues[1]] ?: "" }
        } ?: ""

    private companion object {
        val PLACEHOLDER = Regex("""\$\{([a-zA-Z0-9_]+)}""")
    }
}
