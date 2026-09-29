/* Куски документов вместе с их векторами и поиск по близости.
 *
 * Это векторная база в самом простом виде: массив в памяти и перебор всех
 * векторов на каждый вопрос. На сотне документов так и надо — отдельная база
 * добавит развёртывание, синхронизацию и ещё один повод упасть, а выигрыша не
 * даст. Считать, когда пора переезжать, просто: перебор миллиона векторов по 256
 * чисел — это сотни миллионов операций на КАЖДЫЙ вопрос.
 *
 * Что даёт настоящая векторная база: приблизительный поиск (HNSW, IVF) — он
 * отвечает за миллисекунды, потому что не перебирает всё, и сознательно иногда
 * промахивается мимо точного ответа. */
import { split } from './chunks.mjs'
import { cosine, embed as defaultEmbed } from './embeddings.mjs'

export const CHUNK_CHARS = 400
export const CHUNK_OVERLAP = 80

export class KnowledgeBase {
  #embed
  #chunks = []

  constructor(embed = defaultEmbed) {
    this.#embed = embed
  }

  /** Режет документ на куски и считает вектор каждого. */
  index(source, text) {
    for (const text_ of split(text, CHUNK_CHARS, CHUNK_OVERLAP)) {
      this.#chunks.push({ source, text: text_, vector: this.#embed(text_) })
    }
  }

  clear() {
    this.#chunks = []
  }

  get size() {
    return this.#chunks.length
  }

  /** Ближайшие к вопросу куски, по убыванию близости.
   *
   *  minScore — порог: всё, что ниже, не возвращается совсем. Без порога на
   *  любой вопрос находится «что-то», и помощник уверенно отвечает по случайному
   *  фрагменту. */
  search(question, limit, minScore) {
    const query = this.#embed(question)
    return this.#chunks
      .map(chunk => ({ chunk, score: cosine(query, chunk.vector) }))
      .filter(m => m.score >= minScore)
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
  }
}
