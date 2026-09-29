package llm

import (
	"encoding/json"
	"strings"
	"testing"
)

// scriptedLlm — модель по сценарию: в тестах агента проверяют не
// сообразительность модели, а поведение цикла вокруг неё.
type scriptedLlm struct {
	script []string
	always string
	calls  int
}

func (s *scriptedLlm) complete(prompt string) (string, error) {
	s.calls++
	if s.always != "" {
		return s.always, nil
	}
	if len(s.script) == 0 {
		return `{"answer":"сценарий кончился"}`, nil
	}
	next := s.script[0]
	s.script = s.script[1:]
	return next, nil
}

func вызов(tool string, args map[string]any) string {
	encoded, _ := json.Marshal(map[string]any{"tool": tool, "args": args})
	return string(encoded)
}

func стендАгента() ([]Product, *scriptedLlm, *AgentLoop, *ToolRegistry) {
	products := Products()
	tools := NewToolRegistry(CatalogTools(products)...)
	llm := &scriptedLlm{}
	return products, llm, NewAgentLoop(tools, llm.complete), tools
}

// Вот ради чего цикл: модель не знает остатков, она их СПРАШИВАЕТ, получает
// ответ и отвечает уже по нему. Один вызов модели так не умеет.
func TestРезультатИнструментаВозвращаетсяМодели(t *testing.T) {
	_, llm, agent, _ := стендАгента()
	llm.script = []string{
		вызов("find_products", map[string]any{"query": "мышь"}),
		`{"answer":"Беспроводная мышь есть, 1990 рублей"}`,
	}

	result := agent.Run("есть ли беспроводная мышь", false)

	if !strings.Contains(result.Text, "1990") {
		t.Fatalf("ответ %q", result.Text)
	}
	if len(result.Trace) != 2 {
		t.Fatalf("шагов в следе %d", len(result.Trace))
	}
	if !strings.Contains(result.Trace[0], "find_products") ||
		!strings.Contains(result.Trace[0], "Беспроводная мышь") {
		t.Fatalf("первый шаг: %q", result.Trace[0])
	}
}

// Имя инструмента приходит из ответа модели, то есть из текста. Всё, чего нет в
// белом списке, обязано отбиваться — и не падением сервиса.
func TestВыдуманныйИнструментНеВызывается(t *testing.T) {
	_, llm, agent, _ := стендАгента()
	llm.script = []string{вызов("drop_database", nil), `{"answer":"понял, так нельзя"}`}

	result := agent.Run("удали базу", false)

	if result.Text != "понял, так нельзя" {
		t.Fatalf("ответ %q", result.Text)
	}
	if !strings.Contains(result.Trace[0], "drop_database") {
		t.Fatalf("первый шаг: %q", result.Trace[0])
	}
}

func TestОтветНеJsonПросимПовторить(t *testing.T) {
	_, llm, agent, _ := стендАгента()
	llm.script = []string{"Конечно! Сейчас посмотрю каталог.", `{"answer":"вот ответ"}`}

	result := agent.Run("есть ли мышь", false)

	if result.Text != "вот ответ" {
		t.Fatalf("ответ %q", result.Text)
	}
	if !strings.Contains(result.Trace[0], "не разобран") {
		t.Fatalf("первый шаг: %q", result.Trace[0])
	}
}

// Без потолка модель ходит по кругу за деньги заказчика, а запрос висит.
func TestШагиКончилисьЧестныйОтказ(t *testing.T) {
	_, llm, agent, _ := стендАгента()
	llm.always = вызов("find_products", map[string]any{"query": "мышь"})

	result := agent.Run("найди что-нибудь", false)

	if !strings.Contains(result.Text, "Не уложился") {
		t.Fatalf("ответ %q", result.Text)
	}
	if len(result.Trace) != MaxSteps || llm.calls != MaxSteps {
		t.Fatalf("шагов %d, вызовов %d", len(result.Trace), llm.calls)
	}
}

// Для модели «покажи остаток» и «зарезервируй сто штук» одинаково полезны.
// Различает их не подсказка, а флаг у инструмента и остановка цикла.
func TestИзменяющийВызовЖдётЧеловека(t *testing.T) {
	products, llm, agent, _ := стендАгента()
	llm.always = вызов("reserve", map[string]any{"title": "Беспроводная мышь", "quantity": 3})

	result := agent.Run("зарезервируй три мыши", false)

	if !strings.Contains(result.AwaitingConfirm, "reserve") {
		t.Fatalf("ожидалось подтверждение, получено %q", result.AwaitingConfirm)
	}
	if products[0].Available != 5 {
		t.Fatalf("остаток тронут без подтверждения: %d", products[0].Available)
	}
}

func TestПодтверждённыйВызовВыполняется(t *testing.T) {
	products, llm, agent, _ := стендАгента()
	llm.script = []string{
		вызов("reserve", map[string]any{"title": "Беспроводная мышь", "quantity": 3}),
		`{"answer":"зарезервировал"}`,
	}

	result := agent.Run("зарезервируй три мыши", true)

	if result.AwaitingConfirm != "" || result.Text != "зарезервировал" {
		t.Fatalf("результат %+v", result)
	}
	if products[0].Available != 2 {
		t.Fatalf("остаток %d", products[0].Available)
	}
}

func TestВКаталогеВидноОпасныеИнструменты(t *testing.T) {
	_, _, _, tools := стендАгента()

	var опасный, обычный bool
	for _, line := range strings.Split(tools.Catalogue(), "\n") {
		if strings.Contains(line, "reserve") && strings.Contains(line, "нужно подтверждение") {
			опасный = true
		}
		if strings.Contains(line, "find_products") && !strings.Contains(line, "подтверждение") {
			обычный = true
		}
	}
	if !опасный || !обычный {
		t.Fatalf("каталог:\n%s", tools.Catalogue())
	}
}
