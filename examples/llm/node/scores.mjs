/* Посмотреть своими глазами, какие числа даёт поиск по близости.
 *
 * Запуск: node scores.mjs
 *
 * Тут и видно, почему порог MIN_SCORE стоит там, где стоит: вопросы по делу дают
 * заметно больше, а вопрос не по теме проваливается почти в ноль. Сделайте STOP
 * в embeddings.mjs пустым множеством и запустите снова — вопрос про масло в
 * двигателе начнёт находить правила возврата. */
import { MIN_SCORE } from './assistant.mjs'
import { KnowledgeBase } from './knowledge.mjs'
import { fill } from './shop.mjs'

const ВОПРОСЫ = [
  'сколько дней на возврат товара',
  'когда вернут деньги за возврат',
  'сколько стоит доставка курьером',
  'есть ли беспроводная мышь и сколько стоит',
  'какая гарантия на электронику',
  'как поменять масло в двигателе автомобиля',
]

const base = await fill(new KnowledgeBase())
console.log(`кусков в базе: ${base.size}, порог: ${MIN_SCORE.toFixed(2)}\n`)
for (const вопрос of ВОПРОСЫ) {
  console.log(вопрос)
  for (const { chunk, score } of base.search(вопрос, 3, -1)) {
    const мимо = score >= MIN_SCORE ? ' ' : '×'
    console.log(`  ${мимо} ${score.toFixed(3)}  ${chunk.source.padEnd(28)} ${chunk.text.slice(0, 58)}`)
  }
  console.log()
}
