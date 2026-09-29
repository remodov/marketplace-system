/* Инструменты агента поверх каталога.
 *
 * Важное здесь не код, а то, что агенту НЕ дано: сменить цену, удалить товар,
 * выполнить произвольный запрос. Набор инструментов — это и есть граница его
 * полномочий, и сужать её надёжнее, чем просить в подсказке «ничего не ломай».
 * Скидку, например, агент придумает охотно, а отвечать за неё будет магазин —
 * поэтому инструмента на цену здесь нет. */

export function catalogTools(products) {
  const byTitle = title => products.find(p => p.title.toLowerCase() === String(title ?? '').trim().toLowerCase())

  return [
    {
      name: 'find_products',
      description: 'найти товары по части названия; аргументы: {"query": "мышь"}',
      run: args => {
        const query = String(args.query ?? '').trim().toLowerCase()
        if (!query) return 'нужен аргумент query'
        const found = products.filter(p => p.title.toLowerCase().includes(query))
        if (found.length === 0) return `ничего не найдено по запросу ${query}`
        return found.map(p => `${p.title} | ${p.price} руб | доступно ${p.available}`).join('\n')
      },
    },
    {
      name: 'stock',
      description: 'остаток товара по названию; аргументы: {"title": "Беспроводная мышь"}',
      run: args => {
        const product = byTitle(args.title)
        return product ? `${product.title}: доступно ${product.available}` : 'товара с таким названием нет'
      },
    },
    {
      name: 'reserve',
      description: 'зарезервировать товар; аргументы: {"title": "Беспроводная мышь", "quantity": 1}',
      mutating: true,
      run: args => {
        const product = byTitle(args.title)
        if (!product) return 'товара с таким названием нет'
        const quantity = Number(args.quantity ?? 1)
        if (quantity > product.available) return 'столько нет в наличии'
        product.available -= quantity
        return `зарезервировано, осталось доступно ${product.available}`
      },
    },
  ]
}
