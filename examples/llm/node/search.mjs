/* Поиск по каталогу фразой: модель превращает запрос в фильтры.
 *
 * Покупатель пишет не «мышь», а «недорогая беспроводная мышь в наличии».
 * Обычный поиск по названию на такой фразе не найдёт ничего: слов «недорогая» и
 * «в наличии» в названиях нет.
 *
 * Три вещи обязательны, без них это нельзя выпускать:
 * 1. Кэш. Одна и та же фраза повторяется чаще, чем кажется, а каждый поход к
 *    модели стоит денег и сотен миллисекунд.
 * 2. Работа без модели. Провайдер лежит, ключ протух, лимит исчерпан — поиск
 *    обязан продолжать работать как обычный текстовый. Пустая выдача с ошибкой —
 *    худшее, что можно показать покупателю.
 * 3. Оборонительный разбор ответа. Модель отвечает текстом, а не типом. Она
 *    вернёт не JSON, добавит «конечно, вот ваш ответ», придумает лишнее поле — и
 *    сделает это в самый неудобный момент. */

/* Промпт тоже код: его меняют, и поведение поиска меняется вместе с ним. */
export const PROMPT = query => `Разбери запрос покупателя интернет-магазина в JSON без пояснений.
Поля: text — что искать по названию, maxPrice — потолок цены в рублях или null,
inStockOnly — true, если покупателю нужен товар в наличии.
Запрос: ${query}
`

/** Фраза как есть, без разбора: сюда приходят, когда провайдера нет или он
 *  ответил не тем. */
export const plainFilters = query => ({ text: query, maxPrice: null, inStockOnly: false })

export class QueryUnderstanding {
  #llm
  /* Кэш здесь самый простой, какой бывает. В настоящем сервисе у него обязаны
     быть срок жизни и потолок размера: Map растёт, пока не кончится память, а
     ответ модели на вчерашний ассортимент устаревает. */
  #cache = new Map()

  /** llm — функция «подсказка → ответ» или null, если провайдера нет. */
  constructor(llm = null) {
    this.#llm = llm
  }

  async understand(query) {
    if (this.#cache.has(query)) return this.#cache.get(query)

    const filters = await this.#ask(query)
    this.#cache.set(query, filters)
    return filters
  }

  async #ask(query) {
    if (!this.#llm) return plainFilters(query)
    try {
      return parseFilters(await this.#llm(PROMPT(query)), query)
    } catch {
      /* Ошибку покупателю не показываем: он спрашивал про мышь, а не про наш
         провайдер. */
      return plainFilters(query)
    }
  }
}

/** Обещание модели — не гарантия: она отвечает текстом, а не типом. */
export function parseFilters(answer, original) {
  let data
  try {
    data = JSON.parse(String(answer).trim())
  } catch {
    return plainFilters(original)
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) return plainFilters(original)

  const text = typeof data.text === 'string' && data.text.trim() ? data.text.trim() : original
  const maxPrice = typeof data.maxPrice === 'number' ? data.maxPrice : null
  return { text, maxPrice, inStockOnly: data.inStockOnly === true }
}

/** Фильтры применяются к каталогу обычным перебором — модель тут больше не
 *  участвует, и это важно: она разбирает запрос, а не ищет. */
export function search(products, filters) {
  const text = (filters.text ?? '').trim().toLowerCase()
  return products
    .filter(p => !text || p.title.toLowerCase().includes(text))
    .filter(p => filters.maxPrice === null || p.price <= filters.maxPrice)
    .filter(p => !filters.inStockOnly || p.available > 0)
}
