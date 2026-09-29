package llm

import (
	"encoding/json"
	"fmt"
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
	if a.llm == nil {
		return Result{Text: "Агент недоступен: не настроен провайдер модели."}
	}

	var trace []string
	var observations strings.Builder

	for step := 1; step <= MaxSteps; step++ {
		raw, err := a.llm(fmt.Sprintf(agentPrompt, a.tools.Catalogue(), task, observations.String()))
		if err != nil {
			return Result{Text: "Модель недоступна, попробуйте позже.", Trace: trace}
		}

		decision, ok := parseDecision(raw)
		if !ok {
			// Модель ответила не JSON. Не падаем и не гадаем — говорим ей об этом
			// наблюдением: следующий заход обычно получается.
			trace = append(trace, fmt.Sprintf("шаг %d: ответ не разобран, просим повторить", step))
			observations.WriteString("\nОтвет не разобран. Верни один JSON-объект.")
			continue
		}

		if answer, has := decision["answer"]; has && answer != nil {
			trace = append(trace, fmt.Sprintf("шаг %d: ответ готов", step))
			return Result{Text: fmt.Sprint(answer), Trace: trace}
		}

		name, _ := decision["tool"].(string)
		tool, found := a.tools.Find(name)
		if !found {
			trace = append(trace, fmt.Sprintf("шаг %d: инструмента «%s» нет", step, name))
			observations.WriteString("\nИнструмента «" + name +
				"» не существует. Доступны только перечисленные выше.")
			continue
		}

		args, _ := decision["args"].(map[string]any)
		if tool.Mutating && !confirmed {
			encoded, _ := json.Marshal(args)
			call := name + " " + string(encoded)
			trace = append(trace, fmt.Sprintf("шаг %d: нужен человек — %s", step, call))
			return Result{Text: "Нужно подтверждение: агент хочет выполнить " + call,
				Trace: trace, AwaitingConfirm: call}
		}

		observation := tool.Run(args)
		trace = append(trace, fmt.Sprintf("шаг %d: %s → %s", step, name,
			strings.ReplaceAll(observation, "\n", ";")))
		observations.WriteString("\nРезультат " + name + ": " + observation)
	}

	// Шаги кончились. Честный отказ лучше последнего ответа модели: она к этому
	// моменту уже ходит по кругу, и её «ответ» ничем не обоснован.
	return Result{Text: fmt.Sprintf("Не уложился в %d шагов. Передаю оператору.", MaxSteps),
		Trace: trace}
}

func parseDecision(answer string) (map[string]any, bool) {
	var decision map[string]any
	if err := json.Unmarshal([]byte(strings.TrimSpace(answer)), &decision); err != nil {
		return nil, false
	}
	return decision, true
}
