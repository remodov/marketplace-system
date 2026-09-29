/* Нарезка документа на куски.
 *
 * Зачем вообще резать: в подсказку модели влезает ограниченное число токенов, а
 * искать надо по смыслу небольшого фрагмента. Целая страница правил — один
 * вектор «про всё сразу», по нему не находится ничего.
 *
 * Два параметра решают всё. Размер куска: слишком мелкий теряет контекст («до 14
 * дней» без того, о чём идёт речь), слишком крупный размывает смысл. Перекрытие:
 * без него предложение, попавшее на стык, теряется — ни в одном куске оно не
 * целое. */

/** Режет по границам предложений, не разрывая слова, и повторяет хвост
 *  предыдущего куска в начале следующего. */
export function split(text, maxChars, overlap) {
  if (maxChars <= 0) throw new Error('размер куска должен быть больше нуля')
  if (overlap < 0 || overlap >= maxChars) throw new Error('перекрытие должно быть меньше размера куска')

  const clean = (text ?? '').trim().replace(/\s+/g, ' ')
  if (!clean) return []

  const chunks = []
  let current = ''

  for (let sentence of sentences(clean)) {
    if (current && current.length + 1 + sentence.length > maxChars) {
      chunks.push(current)
      /* Перекрытие берём только в тот запас, что остался под само предложение:
         иначе хвост вместе с длинным предложением снова вылезет за потолок. */
      const room = maxChars - sentence.length - 1
      current = room > 0 ? tail(current, Math.min(overlap, room)) : ''
    }

    /* Одно предложение длиннее потолка — режем по словам: выбрасывать его
       нельзя, а оставить целым не даёт окно модели. */
    while (sentence.length > maxChars) {
      let cut = sentence.lastIndexOf(' ', maxChars)
      if (cut <= 0) cut = maxChars
      chunks.push(sentence.slice(0, cut).trim())
      sentence = tail(sentence.slice(0, cut), overlap) + sentence.slice(cut)
    }

    current = current ? `${current} ${sentence}` : sentence
  }

  if (current) chunks.push(current)
  return chunks.map(c => c.trim()).filter(Boolean)
}

function sentences(text) {
  return text.split(/(?<=[.!?])\s+/).map(s => s.trim()).filter(Boolean)
}

/** Хвост куска, обрезанный по границе слова, — он же начало следующего. */
function tail(chunk, overlap) {
  if (overlap <= 0) return ''
  if (chunk.length <= overlap) return chunk
  const piece = chunk.slice(-overlap)
  const space = piece.indexOf(' ')
  return space < 0 ? piece : piece.slice(space + 1)
}
