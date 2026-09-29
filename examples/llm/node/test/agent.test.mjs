/* Цикл агента.
 *
 * Модель подменена сценарием: в тестах агента проверяют не сообразительность
 * модели, а поведение цикла вокруг неё — что он делает с неизвестным
 * инструментом, с мусором вместо JSON, с хождением по кругу и с опасным вызовом. */
import assert from 'node:assert/strict'
import { beforeEach, describe, it } from 'node:test'

import { AgentLoop, MAX_STEPS, ToolRegistry } from '../agent.mjs'
import { catalogTools } from '../catalogTools.mjs'
import { products } from '../shop.mjs'

/** Модель по сценарию: отдаёт заготовленные ответы по очереди. */
function scriptedLlm() {
  const state = { script: [], always: null, calls: 0 }
  const llm = () => {
    state.calls++
    if (state.always !== null) return state.always
    return state.script.length ? state.script.shift() : '{"answer":"сценарий кончился"}'
  }
  llm.state = state
  return llm
}

const call = (tool, args = {}) => JSON.stringify({ tool, args })

describe('цикл агента', () => {
  let catalogue, llm, agent, tools

  beforeEach(() => {
    catalogue = products()
    tools = new ToolRegistry(catalogTools(catalogue))
    llm = scriptedLlm()
    agent = new AgentLoop(tools, llm)
  })

  const мышь = () => catalogue.find(p => p.title === 'Беспроводная мышь')

  /* Вот ради чего цикл: модель не знает остатков, она их СПРАШИВАЕТ, получает
     ответ и отвечает уже по нему. Один вызов модели так не умеет. */
  it('модель зовёт инструмент, видит результат и отвечает по нему', async () => {
    llm.state.script = [call('find_products', { query: 'мышь' }),
      '{"answer":"Беспроводная мышь есть, 1990 рублей"}']

    const result = await agent.run('есть ли беспроводная мышь')

    assert.ok(result.text.includes('1990'))
    assert.equal(result.trace.length, 2)
    assert.ok(result.trace[0].includes('find_products'))
    assert.ok(result.trace[0].includes('Беспроводная мышь'))
  })

  /* Имя инструмента приходит из ответа модели, то есть из текста. */
  it('выдуманный инструмент не вызывается, а цикл продолжается', async () => {
    llm.state.script = [call('drop_database'), '{"answer":"понял, так нельзя"}']

    const result = await agent.run('удали базу')

    assert.equal(result.text, 'понял, так нельзя')
    assert.ok(result.trace[0].includes('drop_database'))
  })

  it('модель ответила не JSON — просим повторить, а не падаем', async () => {
    llm.state.script = ['Конечно! Сейчас посмотрю каталог.', '{"answer":"вот ответ"}']

    const result = await agent.run('есть ли мышь')

    assert.equal(result.text, 'вот ответ')
    assert.ok(result.trace[0].includes('не разобран'))
  })

  /* Без потолка модель ходит по кругу за деньги заказчика, а запрос висит. */
  it('шаги кончились — честный отказ, а не бесконечный цикл', async () => {
    llm.state.always = call('find_products', { query: 'мышь' })

    const result = await agent.run('найди что-нибудь')

    assert.ok(result.text.includes('Не уложился'))
    assert.equal(result.trace.length, MAX_STEPS)
    assert.equal(llm.state.calls, MAX_STEPS)
  })

  /* Для модели «покажи остаток» и «зарезервируй сто штук» одинаково полезны. */
  it('изменяющий вызов ждёт человека и без него ничего не меняет', async () => {
    llm.state.always = call('reserve', { title: 'Беспроводная мышь', quantity: 3 })

    const result = await agent.run('зарезервируй три мыши')

    assert.ok(result.awaitingConfirm.includes('reserve'))
    assert.ok(result.text.includes('Нужно подтверждение'))
    assert.equal(мышь().available, 5, 'остаток не тронут без подтверждения')
  })

  it('человек подтвердил — вызов выполняется', async () => {
    llm.state.script = [call('reserve', { title: 'Беспроводная мышь', quantity: 3 }),
      '{"answer":"зарезервировал"}']

    const result = await agent.run('зарезервируй три мыши', true)

    assert.equal(result.awaitingConfirm, null)
    assert.equal(result.text, 'зарезервировал')
    assert.equal(мышь().available, 2)
  })

  it('в каталоге инструментов видно, какие из них опасные', () => {
    const lines = tools.catalogue().split('\n')

    assert.ok(lines.some(l => l.includes('reserve') && l.includes('нужно подтверждение')))
    assert.ok(lines.some(l => l.includes('find_products') && !l.includes('подтверждение')))
  })
})
