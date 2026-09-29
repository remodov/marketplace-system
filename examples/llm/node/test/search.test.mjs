/* Поиск по фразе.
 *
 * Модель подменена: настоящий провайдер в тестах не нужен и вреден — он платный,
 * медленный и отвечает каждый раз по-разному. */
import assert from 'node:assert/strict'
import { beforeEach, describe, it } from 'node:test'

import { QueryUnderstanding, search } from '../search.mjs'
import { products } from '../shop.mjs'

/** Подменённая модель: считает вызовы и умеет ломаться по команде. */
function fakeLlm() {
  const state = { calls: 0, answer: '{"text":"мышь","maxPrice":2000,"inStockOnly":true}', broken: false }
  const llm = () => {
    state.calls++
    if (state.broken) throw new Error('провайдер недоступен')
    return state.answer
  }
  llm.state = state
  return llm
}

describe('поиск по фразе', () => {
  let catalogue, llm, understanding

  beforeEach(() => {
    catalogue = products()
    llm = fakeLlm()
    understanding = new QueryUnderstanding(llm)
  })

  const найти = async фраза => search(catalogue, await understanding.understand(фраза))
  const названия = список => список.map(p => p.title)

  /* «Мышь офисная» дешевле, но её нет на складе, а «Мышь игровая премиум» есть,
     но дороже потолка. Остаётся одна. */
  it('фраза превращается в фильтры: и по названию, и по цене, и по наличию', async () => {
    assert.deepEqual(названия(await найти('недорогая беспроводная мышь в наличии')),
      ['Беспроводная мышь'])
  })

  it('одна и та же фраза не ходит к модели дважды', async () => {
    await найти('мышь подешевле')
    await найти('мышь подешевле')
    await найти('мышь подешевле')

    assert.equal(llm.state.calls, 1, 'походов к модели')
  })

  /* Пустая выдача с ошибкой — худшее, что можно показать покупателю. */
  it('провайдер лежит — ищем как обычный текстовый', async () => {
    llm.state.broken = true

    assert.equal((await найти('мышь')).length, 3)
  })

  it('модель ответила мусором — ищем по исходной фразе, а не падаем', async () => {
    llm.state.answer = 'конечно! вот ваш ответ: мышь дешевле 2000'

    assert.deepEqual(названия(await найти('клавиатура')), ['Механическая клавиатура'])
  })

  it('без провайдера — обычный текстовый поиск', async () => {
    const filters = await new QueryUnderstanding(null).understand('недорогая беспроводная мышь')

    assert.equal(filters.text, 'недорогая беспроводная мышь')
    assert.equal(filters.maxPrice, null)
    assert.equal(filters.inStockOnly, false)
  })
})
