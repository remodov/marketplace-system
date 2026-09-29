"""Поиск по фразе.

Модель подменена: настоящий провайдер в тестах не нужен и вреден — он платный,
медленный и отвечает каждый раз по-разному.
"""
import unittest

import search
import shop


class FakeLlm:
    """Подменённая модель: считает вызовы и умеет ломаться по команде."""

    def __init__(self):
        self.calls = 0
        self.answer = '{"text":"мышь","maxPrice":2000,"inStockOnly":true}'
        self.broken = False

    def __call__(self, prompt):
        self.calls += 1
        if self.broken:
            raise RuntimeError("провайдер недоступен")
        return self.answer


class SearchTest(unittest.TestCase):

    def setUp(self):
        self.catalogue = shop.products()
        self.llm = FakeLlm()
        self.understanding = search.QueryUnderstanding(self.llm)

    def найти(self, фраза):
        return search.search(self.catalogue, self.understanding.understand(фраза))

    def test_фраза_превращается_в_фильтры(self):
        """И по названию, и по цене, и по наличию: «Мышь офисная» дешевле, но её
        нет на складе, а «Мышь игровая премиум» есть, но дороже потолка."""
        found = self.найти("недорогая беспроводная мышь в наличии")

        self.assertEqual(["Беспроводная мышь"], [p["title"] for p in found])

    def test_одна_фраза_не_ходит_к_модели_дважды(self):
        self.найти("мышь подешевле")
        self.найти("мышь подешевле")
        self.найти("мышь подешевле")

        self.assertEqual(1, self.llm.calls, "походов к модели")

    def test_провайдер_лежит_ищем_как_обычно(self):
        """Пустая выдача с ошибкой — худшее, что можно показать покупателю."""
        self.llm.broken = True

        found = self.найти("мышь")

        self.assertEqual(3, len(found), "нашлись все три мыши по названию")

    def test_модель_ответила_мусором(self):
        """Ищем по исходной фразе, а не падаем."""
        self.llm.answer = "конечно! вот ваш ответ: мышь дешевле 2000"

        found = self.найти("клавиатура")

        self.assertEqual(["Механическая клавиатура"], [p["title"] for p in found])

    def test_без_провайдера_обычный_текстовый_поиск(self):
        без_модели = search.QueryUnderstanding(None)

        filters = без_модели.understand("недорогая беспроводная мышь")

        self.assertEqual("недорогая беспроводная мышь", filters.text)
        self.assertIsNone(filters.max_price)
        self.assertFalse(filters.in_stock_only)


if __name__ == "__main__":
    unittest.main()
