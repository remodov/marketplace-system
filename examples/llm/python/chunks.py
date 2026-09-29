"""Нарезка документа на куски.

Зачем вообще резать: в подсказку модели влезает ограниченное число токенов, а
искать надо по смыслу небольшого фрагмента. Целая страница правил — один вектор
«про всё сразу», по нему не находится ничего.

Два параметра решают всё. Размер куска: слишком мелкий теряет контекст («до 14
дней» без того, о чём идёт речь), слишком крупный размывает смысл. Перекрытие:
без него предложение, попавшее на стык, теряется — ни в одном куске оно не целое.
"""
import re

_SPACES = re.compile(r"\s+")
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


def split(text, max_chars, overlap):
    """Режет по границам предложений, не разрывая слова.

    Хвост предыдущего куска повторяется в начале следующего — это и есть
    перекрытие.
    """
    if max_chars <= 0:
        raise ValueError("размер куска должен быть больше нуля")
    if overlap < 0 or overlap >= max_chars:
        raise ValueError("перекрытие должно быть меньше размера куска")

    clean = _SPACES.sub(" ", (text or "").strip())
    if not clean:
        return []

    chunks = []
    current = ""
    for sentence in _sentences(clean):
        if current and len(current) + 1 + len(sentence) > max_chars:
            chunks.append(current)
            # Перекрытие берём только в тот запас, что остался под само
            # предложение: иначе хвост вместе с длинным предложением снова
            # вылезет за потолок.
            room = max_chars - len(sentence) - 1
            current = _tail(current, min(overlap, room)) if room > 0 else ""

        # Одно предложение длиннее потолка — режем по словам: выбрасывать его
        # нельзя, а оставить целым не даёт окно модели.
        while len(sentence) > max_chars:
            cut = sentence.rfind(" ", 0, max_chars)
            if cut <= 0:
                cut = max_chars
            chunks.append(sentence[:cut].strip())
            sentence = _tail(sentence[:cut], overlap) + sentence[cut:]

        current = (current + " " + sentence).strip() if current else sentence

    if current:
        chunks.append(current)
    return [c.strip() for c in chunks if c.strip()]


def _sentences(text):
    return [s.strip() for s in _SENTENCE_END.split(text) if s.strip()]


def _tail(chunk, overlap):
    """Хвост куска, обрезанный по границе слова, — он же начало следующего."""
    if overlap <= 0:
        return ""
    if len(chunk) <= overlap:
        return chunk
    piece = chunk[-overlap:]
    space = piece.find(" ")
    return piece if space < 0 else piece[space + 1:]
