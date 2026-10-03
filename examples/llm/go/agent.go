package llm

import (
	"encoding/json"
	"strings"
)

// MaxSteps — потолок шагов цикла.
const MaxSteps = 5

const agentPrompt = `Ты агент службы поддержки интернет-магазина. Тебе доступны инструменты:
%s

Отвечай ТОЛЬКО одним JSON-объектом, без пояснений вокруг.
Чтобы вызвать инструмент: {"tool": "имя", "args": {...}}
Чтобы ответить покупателю: {"answer": "текст"}

Задача: %s
%s
`

// Tool — инструмент, который агент может вызвать.
//
// Description — не документация для человека, а часть подсказки: по нему модель
// решает, звать инструмент или нет. Расплывчатое описание даёт расплывчатый
// выбор. Mutating отделяет «покажи остаток» от «зарезервируй сто штук»: у модели
// нет способа их различить, а цена ошибки разная.
type Tool struct {
	Name        string
	Description string
	Mutating    bool
	Run         func(args map[string]any) string
}

// ToolRegistry — белый список, а не поиск по имени в коде: имя инструмента
// приходит из ответа модели, то есть из текста, на который влияет пользователь.
type ToolRegistry struct {
	order  []Tool
	byName map[string]Tool
}

func NewToolRegistry(tools ...Tool) *ToolRegistry {
	r := &ToolRegistry{byName: map[string]Tool{}}
	for _, t := range tools {
		r.order = append(r.order, t)
		r.byName[t.Name] = t
	}
	return r
}

func (r *ToolRegistry) Find(name string) (Tool, bool) {
	t, ok := r.byName[name]
	return t, ok
}

// Catalogue — каталог инструментов строками; он уходит в подсказку модели.
func (r *ToolRegistry) Catalogue() string {
	if len(r.order) == 0 {
		return "(инструментов нет)"
	}
	var lines []string
	for _, t := range r.order {
		line := "- " + t.Name + ": " + t.Description
		if t.Mutating {
			line += " (меняет данные, нужно подтверждение)"
		}
		lines = append(lines, line)
	}
	return strings.Join(lines, "\n")
}

// Result — чем кончился цикл. Trace нужен всегда: без следа шагов разбирать
// поведение агента нечем, виден только итоговый текст.
type Result struct {
	Text            string
	Trace           []string
	AwaitingConfirm string
}

// AgentLoop — цикл агента: модель решает, что делать, код делает и возвращает
// результат, модель решает снова — и так, пока не появится ответ или не кончатся
// шаги.
//
// Ровно этим агент отличается от одного вызова модели. Вызов даёт текст; цикл
// даёт действие, результат действия и решение с учётом результата. Всё
// остальное — обвязка вокруг четырёх строк: спросить, выполнить, дописать
// наблюдение, спросить снова.
type AgentLoop struct {
	tools *ToolRegistry
	llm   Complete
}

func NewAgentLoop(tools *ToolRegistry, llm Complete) *AgentLoop {
	return &AgentLoop{tools: tools, llm: llm}
}

// Run выполняет задачу. confirmed означает, что человек уже разрешил изменяющие
// вызовы в этом прогоне.
func (a *AgentLoop) Run(task string, confirmed bool) Result {
	// TODO Б4: цикл агента.
	// Шаг цикла: спросить модель подсказкой agentPrompt (каталог инструментов,
	// задача и накопленные наблюдения), разобрать ответ через parseDecision,
	// сделать одно из четырёх.
	//   {"answer": "..."}      вернуть ответ покупателю;
	//   {"tool": "имя", ...}   найти инструмент в реестре и выполнить, результат
	//                          дописать в наблюдения и пойти на следующий шаг;
	//   инструмента нет        сказать об этом наблюдением, не падать;
	//   ответ не JSON          попросить повторить, не гадать.
	// Изменяющий инструмент (tool.Mutating) без confirmed не выполняется:
	// цикл останавливается и возвращает вызов в AwaitingConfirm.
	// Шагов не больше MaxSteps, и на исходе честный отказ, а не последний
	// ответ модели: к этому моменту она уже ходит по кругу.
	// Каждый шаг дописывать в Trace: без него разбирать поведение агента нечем.
	return Result{}
}

func parseDecision(answer string) (map[string]any, bool) {
	var decision map[string]any
	if err := json.Unmarshal([]byte(strings.TrimSpace(answer)), &decision); err != nil {
		return nil, false
	}
	return decision, true
}
