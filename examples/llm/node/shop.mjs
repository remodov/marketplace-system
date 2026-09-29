/* Данные магазина: правила из общих документов и карточки товаров.
 *
 * Товары попадают в базу знаний намеренно. Половина вопросов покупателя — не про
 * правила, а про ассортимент («есть ли беспроводная мышь до двух тысяч»), и
 * отвечать на них по общим документам нечем. */
import { readdir, readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const DOCS = join(dirname(fileURLToPath(import.meta.url)), '..', 'knowledge')

export const PRODUCTS = [
  { title: 'Беспроводная мышь', price: 1990, available: 5 },
  { title: 'Мышь игровая премиум', price: 7900, available: 3 },
  { title: 'Мышь офисная', price: 890, available: 0 },
  { title: 'Механическая клавиатура', price: 5400, available: 2 },
]

export function products() {
  return PRODUCTS.map(p => ({ ...p }))
}

/** Собирает базу знаний заново: сначала правила, потом карточки товаров. */
export async function fill(base, catalogue = products()) {
  base.clear()
  const files = (await readdir(DOCS)).filter(f => f.endsWith('.md')).sort()
  for (const file of files) {
    base.index(file, await readFile(join(DOCS, file), 'utf8'))
  }
  for (const product of catalogue) {
    base.index(`каталог: ${product.title}`, card(product))
  }
  return base
}

/** Карточка кладётся фразой, а не полями через запятую: вопрос задают словами,
 *  и искать придётся по словам. */
export function card(product) {
  const stock = product.available > 0 ? `В наличии ${product.available} штук.` : 'Нет в наличии.'
  return `Товар «${product.title}». Цена ${product.price} рублей. ${stock}`
}
