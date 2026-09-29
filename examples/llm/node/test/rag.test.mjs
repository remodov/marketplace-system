/* Помощник по документам магазина.
 *
 * Главное, что здесь проверяется, — не «модель ответила», а ЧТО ей положили в
 * подсказку и когда её вообще не позвали. */
import assert from 'node:assert/strict'
import { beforeEach, describe, it } from 'node:test'

import { RagAssistant } from '../assistant.mjs'
import { KnowledgeBase } from '../knowledge.mjs'
import { fill } from '../shop.mjs'

/** Подменённая модель: запоминает последнюю подсказку и считает вызовы. */
function fakeLlm() {
  const state = { calls: 0, lastPrompt: '' }
  const llm = prompt => {
    state.calls++
    state.lastPrompt = prompt
    return 'Ответ по документам магазина.'
  }
  llm.state = state
  return llm
}

describe('помощник по документам', () => {
  let base, llm, assistant

  beforeEach(async () => {
    base = await fill(new KnowledgeBase())
    llm = fakeLlm()
    assistant = new RagAssistant(base, llm)
  })

  it('база собирается из правил магазина и карточек товаров', async () => {
    assert.ok(base.size > 5)

    assert.ok((await assistant.ask('сколько стоит доставка курьером')).sources.includes('delivery.md'))
    assert.ok((await assistant.ask('есть ли беспроводная мышь и сколько стоит'))
      .sources.includes('каталог: Беспроводная мышь'))
  })

  /* Ради этого RAG и делают: в подсказке лежит текст из документа, и ответ
     модели опирается на него, а не на то, что она помнит про магазины вообще. */
  it('найденный фрагмент уезжает в подсказку модели', async () => {
    await assistant.ask('сколько дней на возврат товара')

    assert.equal(llm.state.calls, 1)
    assert.ok(llm.state.lastPrompt.includes('14 дней'))
    assert.ok(llm.state.lastPrompt.includes('сколько дней на возврат товара'))
    assert.ok(llm.state.lastPrompt.includes('Если ответа в них нет, так и скажи'))
  })

  /* Самая дорогая ошибка RAG: ничего не нашли, но всё равно спросили модель.
     Она не скажет «не знаю» — она сочинит срок возврата. */
  it('ничего не нашли — отвечаем «не знаю» и к модели не идём', async () => {
    const answer = await assistant.ask('как поменять масло в двигателе автомобиля')

    assert.equal(answer.grounded, false)
    assert.ok(answer.text.includes('Не нашёл ответа'))
    assert.deepEqual(answer.sources, [])
    assert.equal(llm.state.calls, 0, 'походов к модели')
  })

  it('у ответа всегда видно источник', async () => {
    const answer = await assistant.ask('когда вернут деньги за возврат')

    assert.equal(answer.grounded, true)
    assert.ok(answer.sources.length > 0)
    assert.equal(answer.text, 'Ответ по документам магазина.')
  })

  /* Половина пользы RAG — в поиске: человек видит нужный абзац, даже когда
     сформулировать ответ некому. */
  it('без провайдера отдаём найденный фрагмент', async () => {
    const answer = await new RagAssistant(base, null).ask('сколько стоит доставка курьером')

    assert.equal(answer.grounded, true)
    assert.ok(answer.text.includes('390'))
  })
})
