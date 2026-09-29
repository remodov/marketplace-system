"""Нарезка на куски. Ничего, кроме стандартной библиотеки: запускается как
python3 -m unittest."""
import unittest

import chunks

DOC = (
    "Вернуть товар надлежащего качества можно в течение 14 дней с момента получения. "
    "Товар должен сохранить товарный вид, упаковку и все комплектующие. "
    "Деньги возвращаются тем же способом, которым была оплата, в течение десяти рабочих дней. "
    "Товар с недостатком можно вернуть в течение гарантийного срока. "
    "Доставку до склада в этом случае оплачивает магазин."
)


def last_word(chunk):
    return chunk.strip().split(" ")[-1]


class ChunksTest(unittest.TestCase):

    def test_ни_один_кусок_не_длиннее_потолка(self):
        for chunk in chunks.split(DOC, 120, 30):
            self.assertLessEqual(len(chunk), 120, chunk)

    def test_соседние_куски_перекрываются(self):
        """Перекрытие — единственное, что спасает предложение на стыке. Без него
        вопрос про «десять рабочих дней» не найдёт ни один кусок."""
        pieces = chunks.split(DOC, 120, 30)

        self.assertGreater(len(pieces), 1)
        self.assertIn(last_word(pieces[0]), pieces[1])

    def test_слова_не_рвутся(self):
        for chunk in chunks.split(DOC, 120, 30):
            self.assertIn(last_word(chunk), DOC)

    def test_длинное_предложение_режется_по_словам(self):
        long_one = "слово " * 100 + "конец."

        pieces = chunks.split(long_one, 100, 20)

        for chunk in pieces:
            self.assertLessEqual(len(chunk), 100)
        self.assertIn("конец.", " ".join(pieces))

    def test_пустой_документ_даёт_пустой_список(self):
        self.assertEqual([], chunks.split("   ", 100, 20))
        self.assertEqual([], chunks.split(None, 100, 20))


if __name__ == "__main__":
    unittest.main()
