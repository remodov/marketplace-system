"""Куски документов вместе с их векторами и поиск по близости.

Это векторная база в самом простом виде: список в памяти и перебор всех
векторов на каждый вопрос. На сотне документов так и надо — отдельная база
добавит развёртывание, синхронизацию и ещё один повод упасть, а выигрыша не
даст. Считать, когда пора переезжать, просто: перебор миллиона векторов по 256
чисел — это сотни миллионов операций на КАЖДЫЙ вопрос.

Что даёт настоящая векторная база: приблизительный поиск (HNSW, IVF) — он
отвечает за миллисекунды, потому что не перебирает всё, и сознательно иногда
промахивается мимо точного ответа.
"""
from dataclasses import dataclass

import chunks
import embeddings

CHUNK_CHARS = 400
CHUNK_OVERLAP = 80


@dataclass(frozen=True)
class Chunk:
    source: str
    text: str
    vector: tuple


@dataclass(frozen=True)
class Match:
    chunk: Chunk
    score: float


class KnowledgeBase:

    def __init__(self, embed=embeddings.embed):
        self._embed = embed
        self._chunks = []

    def index(self, source, text):
        """Режет документ на куски и считает вектор каждого."""
        for piece in chunks.split(text, CHUNK_CHARS, CHUNK_OVERLAP):
            self._chunks.append(Chunk(source, piece, tuple(self._embed(piece))))

    def clear(self):
        self._chunks.clear()

    def size(self):
        return len(self._chunks)

    def search(self, question, limit, min_score):
        """Ближайшие к вопросу куски, по убыванию близости.

        min_score — порог: всё, что ниже, не возвращается совсем. Без порога на
        любой вопрос находится «что-то», и помощник уверенно отвечает по
        случайному фрагменту.
        """
        query = self._embed(question)
        found = []
        for chunk in self._chunks:
            score = cosine(query, chunk.vector)
            if score >= min_score:
                found.append(Match(chunk, score))
        found.sort(key=lambda m: m.score, reverse=True)
        return found[:limit]


def cosine(a, b):
    """Векторы нормированы, поэтому косинус — просто скалярное произведение."""
    return sum(x * y for x, y in zip(a, b))
