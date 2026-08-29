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

    // TODO Б1: перенести подстановку на Kotlin.
    // Java-версия лежала рядом и делала это циклом по Matcher; здесь короче:
    // Regex.replace принимает лямбду, а отсутствующее значение закрывается
    // элвис-оператором. Пустые поля шаблона — обычный случай, не ошибка.
    fun render(template: TemplatesPojo, variables: Map<String, String>): Rendered =
        Rendered(subject = "", body = "")

    private companion object {
        val PLACEHOLDER = Regex("""\$\{([a-zA-Z0-9_]+)}""")
    }
}
