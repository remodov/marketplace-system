/* Нарезка на куски. Ничего, кроме стандартной библиотеки: запускается как
   node --test. */
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { split } from '../chunks.mjs'

const DOC = 'Вернуть товар надлежащего качества можно в течение 14 дней с момента получения. '
  + 'Товар должен сохранить товарный вид, упаковку и все комплектующие. '
  + 'Деньги возвращаются тем же способом, которым была оплата, в течение десяти рабочих дней. '
  + 'Товар с недостатком можно вернуть в течение гарантийного срока. '
  + 'Доставку до склада в этом случае оплачивает магазин.'

const lastWord = chunk => chunk.trim().split(' ').at(-1)

describe('нарезка на куски', () => {
  it('ни один кусок не длиннее потолка', () => {
    for (const chunk of split(DOC, 120, 30)) {
      assert.ok(chunk.length <= 120, chunk)
    }
  })

  /* Перекрытие — единственное, что спасает предложение на стыке кусков. Без него
     вопрос про «десять рабочих дней» не найдёт ни один кусок. */
  it('соседние куски перекрываются хвостом предыдущего', () => {
    const chunks = split(DOC, 120, 30)

    assert.ok(chunks.length > 1)
    assert.ok(chunks[1].includes(lastWord(chunks[0])))
  })

  it('слова не рвутся посередине', () => {
    for (const chunk of split(DOC, 120, 30)) {
      assert.ok(DOC.includes(lastWord(chunk)))
    }
  })

  it('предложение длиннее потолка режется по словам, а не выбрасывается', () => {
    const chunks = split('слово '.repeat(100) + 'конец.', 100, 20)

    for (const chunk of chunks) assert.ok(chunk.length <= 100)
    assert.ok(chunks.join(' ').includes('конец.'))
  })

  it('пустой документ даёт пустой список', () => {
    assert.deepEqual(split('   ', 100, 20), [])
    assert.deepEqual(split(null, 100, 20), [])
  })
})
