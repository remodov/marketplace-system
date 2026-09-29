/* Цикл агента: модель решает, что делать, код делает и возвращает результат,
 * модель решает снова — и так, пока не появится ответ или не кончатся шаги.
 *
 * Ровно этим агент отличается от одного вызова модели. Вызов даёт текст; цикл
 * даёт действие, результат действия и решение с учётом результата. Всё остальное
 * — обвязка вокруг четырёх строк: спросить → выполнить → дописать наблюдение →
 * спросить снова.
 *
 * Три вещи, без которых цикл нельзя выпускать наружу:
 * 1. Предел шагов. Модель умеет ходить по кругу: вызвала поиск, не поняла ответ,
 *    вызвала снова. Без потолка это бесконечные деньги и висящий запрос.
 * 2. Белый список инструментов. Имя инструмента приходит из ответа модели, то
 *    есть из текста, на который влияет пользователь.
 * 3. Подтверждение на изменяющие действия. Прочитать остаток и зарезервировать
 *    сто штук для модели одинаково «полезно». */

export const MAX_STEPS = 5

export const PROMPT = (tools, task, observations) =>
  `Ты агент службы поддержки интернет-магазина. Тебе доступны инструменты:
${tools}

Отвечай ТОЛЬКО одним JSON-объектом, без пояснений вокруг.
Чтобы вызвать инструмент: {"tool": "имя", "args": {...}}
Чтобы ответить покупателю: {"answer": "текст"}

Задача: ${task}
${observations}
`

/** Белый список, а не поиск по имени в коде: всё, чего нет в списке, должно
 *  отбиваться на входе — иначе ответ модели превращается в команду серверу. */
export class ToolRegistry {
  #byName

  constructor(tools) {
    this.#byName = new Map(tools.map(t => [t.name, t]))
  }

  find(name) {
    return this.#byName.get(name) ?? null
  }

  /** Каталог инструментов строками — он уходит в подсказку модели. */
  catalogue() {
    if (this.#byName.size === 0) return '(инструментов нет)'
    return [...this.#byName.values()]
      .map(t => `- ${t.name}: ${t.description}${t.mutating ? ' (меняет данные, нужно подтверждение)' : ''}`)
      .join('\n')
  }
}

export class AgentLoop {
  #tools
  #llm

  constructor(tools, llm) {
    this.#tools = tools
    this.#llm = llm
  }

  /** confirmed — человек уже разрешил изменяющие вызовы в этом прогоне. */
  async run(task, confirmed = false) {
    if (!this.#llm) {
      return { text: 'Агент недоступен: не настроен провайдер модели.', trace: [], awaitingConfirm: null }
    }

    const trace = []
    let observations = ''

    for (let step = 1; step <= MAX_STEPS; step++) {
      let raw
      try {
        raw = await this.#llm(PROMPT(this.#tools.catalogue(), task, observations))
      } catch {
        return { text: 'Модель недоступна, попробуйте позже.', trace, awaitingConfirm: null }
      }

      const decision = parse(raw)
      if (!decision) {
        /* Модель ответила не JSON. Не падаем и не гадаем — говорим ей об этом
           наблюдением: следующий заход обычно получается. */
        trace.push(`шаг ${step}: ответ не разобран, просим повторить`)
        observations += '\nОтвет не разобран. Верни один JSON-объект.'
        continue
      }

      if (decision.answer != null) {
        trace.push(`шаг ${step}: ответ готов`)
        return { text: String(decision.answer), trace, awaitingConfirm: null }
      }

      const name = decision.tool ?? ''
      const tool = this.#tools.find(name)
      if (!tool) {
        trace.push(`шаг ${step}: инструмента «${name}» нет`)
        observations += `\nИнструмента «${name}» не существует. Доступны только перечисленные выше.`
        continue
      }

      const args = decision.args ?? {}
      if (tool.mutating && !confirmed) {
        const call = `${name} ${JSON.stringify(args)}`
        trace.push(`шаг ${step}: нужен человек — ${call}`)
        return { text: `Нужно подтверждение: агент хочет выполнить ${call}`, trace, awaitingConfirm: call }
      }

      const observation = await tool.run(args)
      trace.push(`шаг ${step}: ${name} → ${observation.replaceAll('\n', ';')}`)
      observations += `\nРезультат ${name}: ${observation}`
    }

    /* Шаги кончились. Честный отказ лучше последнего ответа модели: она к этому
       моменту уже ходит по кругу, и её «ответ» ничем не обоснован. */
    return { text: `Не уложился в ${MAX_STEPS} шагов. Передаю оператору.`, trace, awaitingConfirm: null }
  }
}

function parse(answer) {
  try {
    const value = JSON.parse(String(answer).trim())
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null
  } catch {
    return null
  }
}
