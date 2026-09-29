"""Помощник по документам магазина.

Главное, что здесь проверяется, — не «модель ответила», а ЧТО ей положили в
подсказку и когда её вообще не позвали.
"""
import unittest

import assistant
import knowledge
import shop


class FakeLlm:
    """Подменённая модель: запоминает последнюю подсказку и считает вызовы."""

    def __init__(self):
        self.calls = 0
        self.last_prompt = ""

    def __call__(self, prompt):
        self.calls += 1
        self.last_prompt = prompt
        return "Ответ по документам магазина."


class RagTest(unittest.TestCase):

    def setUp(self):
        self.base = shop.fill(knowledge.KnowledgeBase())
        self.llm = FakeLlm()
        self.assistant = assistant.RagAssistant(self.base, self.llm)

    def test_база_из_правил_и_карточек_товаров(self):
        self.assertGreater(self.base.size(), 5)

        self.assertIn("delivery.md", self.assistant.ask("сколько стоит доставка курьером").sources)
        self.assertIn("каталог: Беспроводная мышь",
                      self.assistant.ask("есть ли беспроводная мышь и сколько стоит").sources)

    def test_найденный_фрагмент_уезжает_в_подсказку(self):
        """Ради этого RAG и делают: в подсказке лежит текст из документа, и ответ
        модели опирается на него, а не на то, что она помнит про магазины вообще."""
        self.assistant.ask("сколько дней на возврат товара")

        self.assertEqual(1, self.llm.calls)
        self.assertIn("14 дней", self.llm.last_prompt)
        self.assertIn("сколько дней на возврат товара", self.llm.last_prompt)
        self.assertIn("Если ответа в них нет, так и скажи", self.llm.last_prompt)

    def test_ничего_не_нашли_отказ_без_похода_к_модели(self):
        """Самая дорогая ошибка RAG: ничего не нашли, но всё равно спросили модель.
        Она не скажет «не знаю» — она сочинит срок возврата."""
        answer = self.assistant.ask("как поменять масло в двигателе автомобиля")

        self.assertFalse(answer.grounded)
        self.assertIn("Не нашёл ответа", answer.text)
        self.assertEqual([], answer.sources)
        self.assertEqual(0, self.llm.calls, "походов к модели")

    def test_у_ответа_всегда_видно_источник(self):
        answer = self.assistant.ask("когда вернут деньги за возврат")

        self.assertTrue(answer.grounded)
        self.assertTrue(answer.sources)
        self.assertEqual("Ответ по документам магазина.", answer.text)

    def test_без_провайдера_отдаём_найденный_фрагмент(self):
        """Половина пользы RAG — в поиске: человек видит нужный абзац, даже когда
        сформулировать ответ некому."""
        без_модели = assistant.RagAssistant(self.base, None)

        answer = без_модели.ask("сколько стоит доставка курьером")

        self.assertTrue(answer.grounded)
        self.assertIn("390", answer.text)


if __name__ == "__main__":
    unittest.main()
