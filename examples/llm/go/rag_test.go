package llm

import (
	"path/filepath"
	"strings"
	"testing"
)

// fakeLlm — подменённая модель: запоминает последнюю подсказку и считает вызовы.
// Настоящий провайдер в тестах не нужен и вреден: он платный, медленный и
// отвечает каждый раз по-разному.
type fakeLlm struct {
	calls      int
	lastPrompt string
}

func (f *fakeLlm) complete(prompt string) (string, error) {
	f.calls++
	f.lastPrompt = prompt
	return "Ответ по документам магазина.", nil
}

func стенд(t *testing.T) (*KnowledgeBase, *fakeLlm, *RagAssistant) {
	t.Helper()
	base := NewKnowledgeBase(HashingEmbed)
	if err := FillKnowledge(base, filepath.Join("..", "knowledge"), Products()); err != nil {
		t.Fatalf("база знаний не собралась: %v", err)
	}
	llm := &fakeLlm{}
	return base, llm, NewRagAssistant(base, llm.complete)
}

func TestБазаИзПравилИКарточекТоваров(t *testing.T) {
	base, _, assistant := стенд(t)

	if base.Size() < 5 {
		t.Fatalf("кусков всего %d", base.Size())
	}
	if !содержит(assistant.Ask("сколько стоит доставка курьером").Sources, "delivery.md") {
		t.Fatal("правила доставки не нашлись")
	}
	if !содержит(assistant.Ask("есть ли беспроводная мышь и сколько стоит").Sources,
		"каталог: Беспроводная мышь") {
		t.Fatal("карточка товара не нашлась")
	}
}

// Ради этого RAG и делают: в подсказке лежит текст из документа, и ответ модели
// опирается на него, а не на то, что она помнит про магазины вообще.
func TestНайденныйФрагментУезжаетВПодсказку(t *testing.T) {
	_, llm, assistant := стенд(t)

	assistant.Ask("сколько дней на возврат товара")

	if llm.calls != 1 {
		t.Fatalf("походов к модели %d", llm.calls)
	}
	for _, ожидалось := range []string{"14 дней", "сколько дней на возврат товара",
		"Если ответа в них нет, так и скажи"} {
		if !strings.Contains(llm.lastPrompt, ожидалось) {
			t.Fatalf("в подсказке нет %q", ожидалось)
		}
	}
}

// Самая дорогая ошибка RAG: ничего не нашли, но всё равно спросили модель. Она
// не скажет «не знаю» — она сочинит срок возврата.
func TestНичегоНеНашлиОтказБезПоходаКМодели(t *testing.T) {
	_, llm, assistant := стенд(t)

	answer := assistant.Ask("как поменять масло в двигателе автомобиля")

	if answer.Grounded {
		t.Fatal("ответ помечен обоснованным, хотя источников нет")
	}
	if !strings.Contains(answer.Text, "Не нашёл ответа") {
		t.Fatalf("вместо отказа: %q", answer.Text)
	}
	if llm.calls != 0 {
		t.Fatalf("походов к модели %d, ожидался ноль", llm.calls)
	}
}

func TestБезПровайдераОтдаёмНайденныйФрагмент(t *testing.T) {
	base, _, _ := стенд(t)
	assistant := NewRagAssistant(base, nil)

	answer := assistant.Ask("сколько стоит доставка курьером")

	if !answer.Grounded || !strings.Contains(answer.Text, "390") {
		t.Fatalf("ожидался найденный фрагмент, получено %q", answer.Text)
	}
}

func содержит(список []string, значение string) bool {
	for _, s := range список {
		if s == значение {
			return true
		}
	}
	return false
}
