"""Цикл агента.

Модель подменена сценарием: в тестах агента проверяют не сообразительность
модели, а поведение цикла вокруг неё — что он делает с неизвестным инструментом,
с мусором вместо JSON, с хождением по кругу и с опасным вызовом.
"""
import json
import unittest

import agent
import catalog_tools
import shop


class ScriptedLlm:
    """Модель по сценарию: отдаёт заготовленные ответы по очереди."""

    def __init__(self):
        self.script = []
        self.always = None
        self.calls = 0

    def __call__(self, prompt):
        self.calls += 1
        if self.always is not None:
            return self.always
        return self.script.pop(0) if self.script else '{"answer":"сценарий кончился"}'


def call(tool, **args):
    return json.dumps({"tool": tool, "args": args}, ensure_ascii=False)


class AgentTest(unittest.TestCase):

    def setUp(self):
        self.products = [dict(p) for p in shop.PRODUCTS]
        self.tools = agent.ToolRegistry(catalog_tools.build(self.products))
        self.llm = ScriptedLlm()
        self.agent = agent.AgentLoop(self.tools, self.llm)

    def мышь(self):
        return next(p for p in self.products if p["title"] == "Беспроводная мышь")

    def test_результат_инструмента_возвращается_модели(self):
        """Вот ради чего цикл: модель не знает остатков, она их СПРАШИВАЕТ,
        получает ответ и отвечает уже по нему. Один вызов так не умеет."""
        self.llm.script = [call("find_products", query="мышь"),
                           '{"answer":"Беспроводная мышь есть, 1990 рублей"}']

        result = self.agent.run("есть ли беспроводная мышь")

        self.assertIn("1990", result.text)
        self.assertEqual(2, len(result.trace))
        self.assertIn("find_products", result.trace[0])
        self.assertIn("Беспроводная мышь", result.trace[0])

    def test_выдуманный_инструмент_не_вызывается(self):
        """Имя инструмента приходит из ответа модели, то есть из текста."""
        self.llm.script = [call("drop_database"), '{"answer":"понял, так нельзя"}']

        result = self.agent.run("удали базу")

        self.assertEqual("понял, так нельзя", result.text)
        self.assertIn("drop_database", result.trace[0])
        self.assertIn("нет", result.trace[0])

    def test_ответ_не_json_просим_повторить(self):
        self.llm.script = ["Конечно! Сейчас посмотрю каталог.", '{"answer":"вот ответ"}']

        result = self.agent.run("есть ли мышь")

        self.assertEqual("вот ответ", result.text)
        self.assertIn("не разобран", result.trace[0])

    def test_шаги_кончились_честный_отказ(self):
        """Без потолка модель ходит по кругу за деньги заказчика."""
        self.llm.always = call("find_products", query="мышь")

        result = self.agent.run("найди что-нибудь")

        self.assertIn("Не уложился", result.text)
        self.assertEqual(agent.MAX_STEPS, len(result.trace))
        self.assertEqual(agent.MAX_STEPS, self.llm.calls)

    def test_изменяющий_вызов_ждёт_человека(self):
        """Для модели «покажи остаток» и «зарезервируй сто штук» одинаково полезны."""
        self.llm.always = call("reserve", title="Беспроводная мышь", quantity=3)

        result = self.agent.run("зарезервируй три мыши")

        self.assertIn("reserve", result.awaiting_confirm)
        self.assertIn("Нужно подтверждение", result.text)
        self.assertEqual(5, self.мышь()["available"], "остаток не тронут без подтверждения")

    def test_подтверждённый_вызов_выполняется(self):
        self.llm.script = [call("reserve", title="Беспроводная мышь", quantity=3),
                           '{"answer":"зарезервировал"}']

        result = self.agent.run("зарезервируй три мыши", confirmed=True)

        self.assertIsNone(result.awaiting_confirm)
        self.assertEqual("зарезервировал", result.text)
        self.assertEqual(2, self.мышь()["available"])

    def test_в_каталоге_видно_опасные_инструменты(self):
        lines = self.tools.catalogue().split("\n")

        self.assertTrue(any("reserve" in l and "нужно подтверждение" in l for l in lines))
        self.assertTrue(any("find_products" in l and "подтверждение" not in l for l in lines))


if __name__ == "__main__":
    unittest.main()
