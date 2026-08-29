package ru.vikulinva.notificationservice.template

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.vikulinva.notificationservice.generated.tables.pojos.TemplatesPojo
import kotlin.test.assertEquals

/**
 * Тесты рендерера на Kotlin: то же поведение, что и раньше, но проверяется
 * из языка, на котором сервис теперь и написан.
 */
class TemplateRendererTest {

    private val renderer = TemplateRenderer()

    private fun template(subject: String?, body: String?) = TemplatesPojo().apply {
        this.subject = subject
        this.body = body
    }

    @Test
    @DisplayName("подставляет значения в тему и тело")
    fun substitutesVariables() {
        val rendered = renderer.render(
            template("Заказ \${orderId} оформлен", "Здравствуйте, \${name}! Сумма: \${total} ₽"),
            mapOf("orderId" to "A-17", "name" to "Мария", "total" to "1990.00"),
        )

        assertEquals("Заказ A-17 оформлен", rendered.subject)
        assertEquals("Здравствуйте, Мария! Сумма: 1990.00 ₽", rendered.body)
    }

    @Test
    @DisplayName("неизвестная переменная превращается в пустую строку, а не в дыру в письме")
    fun unknownVariableBecomesEmpty() {
        val rendered = renderer.render(template("Тема", "Привет, \${missing}!"), mapOf())

        assertEquals("Привет, !", rendered.body)
    }

    @Test
    @DisplayName("пустой шаблон не роняет отправку")
    fun nullFieldsAreSafe() {
        val rendered = renderer.render(template(null, null), mapOf("name" to "Мария"))

        assertEquals("", rendered.subject)
        assertEquals("", rendered.body)
    }

    @Test
    @DisplayName("текст без плейсхолдеров остаётся собой")
    fun plainTextIsUntouched() {
        val rendered = renderer.render(template("Тема", "Просто текст"), mapOf("name" to "Мария"))

        assertEquals("Просто текст", rendered.body)
    }

    @Test
    @DisplayName("значение со спецсимволами подставляется как есть")
    fun specialCharactersAreLiteral() {
        val rendered = renderer.render(template("Тема", "Ссылка: \${url}"),
            mapOf("url" to "https://example.com/?a=1&b=\$2"))

        assertEquals("Ссылка: https://example.com/?a=1&b=\$2", rendered.body)
    }
}
