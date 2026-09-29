"""Цикл агента: модель решает, что делать, код делает и возвращает результат,
модель решает снова — и так, пока не появится ответ или не кончатся шаги.

Ровно этим агент отличается от одного вызова модели. Вызов даёт текст; цикл
даёт действие, результат действия и решение с учётом результата. Всё остальное —
обвязка вокруг четырёх строк: спросить → выполнить → дописать наблюдение →
спросить снова.

Три вещи, без которых цикл нельзя выпускать наружу:
1. Предел шагов. Модель умеет ходить по кругу: вызвала поиск, не поняла ответ,
   вызвала снова. Без потолка это бесконечные деньги и висящий запрос.
2. Белый список инструментов. Имя инструмента приходит из ответа модели, то есть
   из текста, на который влияет пользователь.
3. Подтверждение на изменяющие действия. Прочитать остаток и зарезервировать сто
   штук для модели одинаково «полезно».
"""
import json
from dataclasses import dataclass, field

MAX_STEPS = 5

PROMPT = """Ты агент службы поддержки интернет-магазина. Тебе доступны инструменты:
{tools}

Отвечай ТОЛЬКО одним JSON-объектом, без пояснений вокруг.
Чтобы вызвать инструмент: {{"tool": "имя", "args": {{...}}}}
Чтобы ответить покупателю: {{"answer": "текст"}}

Задача: {task}
{observations}
"""


@dataclass(frozen=True)
class Tool:
    """Описание — не документация для человека, а часть подсказки: по нему модель
    решает, звать инструмент или нет. Расплывчатое описание даёт расплывчатый
    выбор. Флаг mutating отделяет «покажи остаток» от «зарезервируй сто штук»."""
    name: str
    description: str
    run: object
    mutating: bool = False


class ToolRegistry:
    """Белый список, а не поиск по имени в коде: всё, чего нет в списке, должно
    отбиваться на входе — иначе ответ модели превращается в команду серверу."""

    def __init__(self, tools):
        self._by_name = {t.name: t for t in tools}

    def find(self, name):
        return self._by_name.get(name)

    def catalogue(self):
        """Каталог инструментов строками — он уходит в подсказку модели."""
        return "\n".join(
            "- %s: %s%s" % (t.name, t.description,
                            " (меняет данные, нужно подтверждение)" if t.mutating else "")
            for t in self._by_name.values()) or "(инструментов нет)"


@dataclass(frozen=True)
class Result:
    """text — ответ или объяснение, почему его нет; trace — что агент делал по
    шагам, без этого разбирать нечего; awaiting_confirm — вызов, который ждёт
    человека."""
    text: str
    trace: list = field(default_factory=list)
    awaiting_confirm: str = None


class AgentLoop:

    def __init__(self, tools, llm):
        self._tools = tools
        self._llm = llm

    def run(self, task, confirmed=False):
        if self._llm is None:
            return Result("Агент недоступен: не настроен провайдер модели.")

        trace = []
        observations = ""

        for step in range(1, MAX_STEPS + 1):
            try:
                raw = self._llm(PROMPT.format(tools=self._tools.catalogue(),
                                              task=task, observations=observations))
            except Exception:
                return Result("Модель недоступна, попробуйте позже.", list(trace))

            decision = _parse(raw)
            if decision is None:
                # Модель ответила не JSON. Не падаем и не гадаем — говорим ей об
                # этом наблюдением: следующий заход обычно получается.
                trace.append("шаг %d: ответ не разобран, просим повторить" % step)
                observations += "\nОтвет не разобран. Верни один JSON-объект."
                continue

            if decision.get("answer") is not None:
                trace.append("шаг %d: ответ готов" % step)
                return Result(str(decision["answer"]), list(trace))

            name = decision.get("tool", "")
            tool = self._tools.find(name)
            if tool is None:
                trace.append("шаг %d: инструмента «%s» нет" % (step, name))
                observations += ("\nИнструмента «%s» не существует. "
                                 "Доступны только перечисленные выше." % name)
                continue

            args = decision.get("args") or {}
            if tool.mutating and not confirmed:
                call = "%s %s" % (name, json.dumps(args, ensure_ascii=False))
                trace.append("шаг %d: нужен человек — %s" % (step, call))
                return Result("Нужно подтверждение: агент хочет выполнить " + call,
                              list(trace), call)

            observation = tool.run(args)
            trace.append("шаг %d: %s → %s" % (step, name, observation.replace("\n", ";")))
            observations += "\nРезультат %s: %s" % (name, observation)

        # Шаги кончились. Честный отказ лучше последнего ответа модели: она к
        # этому моменту уже ходит по кругу, и её «ответ» ничем не обоснован.
        return Result("Не уложился в %d шагов. Передаю оператору." % MAX_STEPS, list(trace))


def _parse(answer):
    try:
        value = json.loads(answer.strip())
        return value if isinstance(value, dict) else None
    except Exception:
        return None
