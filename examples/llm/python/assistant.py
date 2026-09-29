"""Ответ на вопрос покупателя по документам магазина.

Схема целиком: вопрос → поиск похожих кусков → подсказка из найденного → ответ
модели. Модель тут ничего не знает про магазин и не должна знать: всё, чем она
располагает, лежит в подсказке. Это и есть смысл RAG — знание приносят из базы,
а не из весов модели.

Главное правило, из-за которого всё и затевалось: ничего не нашли — отвечаем
«не знаю», а не зовём модель. Модель, которой не дали контекст, не молчит — она
сочиняет правдоподобный ответ про сроки возврата, и по нему покупатель идёт к
оператору. Отказ дешевле выдуманного ответа.
"""
from dataclasses import dataclass, field

# Сколько кусков кладём в подсказку. Больше — дороже и хуже: лишние куски
# разбавляют нужный и уводят ответ в сторону.
TOP_K = 3

# Порог близости. Не угадан, а измерен на этих документах: вопросы по делу дают
# заметно больше, вопрос не по теме — ровно ноль. Со смысловой моделью
# эмбеддингов порог подбирают заново, глядя на выдачу.
MIN_SCORE = 0.15

PROMPT = """Ты помощник интернет-магазина. Ответь на вопрос покупателя, опираясь
ТОЛЬКО на фрагменты ниже. Если ответа в них нет, так и скажи — не придумывай.
Отвечай коротко, двумя-тремя предложениями.

Фрагменты:
{context}

Вопрос: {question}
"""


@dataclass(frozen=True)
class Answer:
    text: str
    sources: list = field(default_factory=list)
    grounded: bool = False


class RagAssistant:

    def __init__(self, base, llm=None):
        """llm — функция «подсказка → ответ» или None, если провайдера нет."""
        self._base = base
        self._llm = llm

    def ask(self, question):
        found = self._base.search(question, TOP_K, MIN_SCORE)
        if not found:
            return Answer("Не нашёл ответа в документах магазина. "
                          "Спросите оператора — он посмотрит вручную.", [], False)

        sources = list(dict.fromkeys(m.chunk.source for m in found))
        context = "\n".join("- " + m.chunk.text for m in found)

        if self._llm is None:
            # Без провайдера отдаём найденное как есть. Половина пользы RAG —
            # именно в поиске: человек видит нужный абзац, даже когда
            # сформулировать ответ некому.
            return Answer(found[0].chunk.text, sources, True)
        try:
            text = self._llm(PROMPT.format(context=context, question=question))
            return Answer(text.strip(), sources, True)
        except Exception:
            return Answer(found[0].chunk.text, sources, True)
