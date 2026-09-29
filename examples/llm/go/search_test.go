package llm

import (
	"errors"
	"testing"
)

// fakeSearchLlm — подменённая модель: считает вызовы и умеет ломаться по команде.
type fakeSearchLlm struct {
	calls  int
	answer string
	broken bool
}

func (f *fakeSearchLlm) complete(prompt string) (string, error) {
	f.calls++
	if f.broken {
		return "", errors.New("провайдер недоступен")
	}
	return f.answer, nil
}

func стендПоиска() ([]Product, *fakeSearchLlm, *QueryUnderstanding) {
	llm := &fakeSearchLlm{answer: `{"text":"мышь","maxPrice":2000,"inStockOnly":true}`}
	return Products(), llm, NewQueryUnderstanding(llm.complete)
}

func названия(products []Product) []string {
	var out []string
	for _, p := range products {
		out = append(out, p.Title)
	}
	return out
}

// «Мышь офисная» дешевле, но её нет на складе, а «Мышь игровая премиум» есть, но
// дороже потолка. Остаётся одна.
func TestФразаПревращаетсяВФильтры(t *testing.T) {
	catalogue, _, understanding := стендПоиска()

	found := SearchCatalog(catalogue, understanding.Understand("недорогая беспроводная мышь в наличии"))

	if len(found) != 1 || found[0].Title != "Беспроводная мышь" {
		t.Fatalf("нашлось %v", названия(found))
	}
}

func TestОднаФразаНеХодитКМоделиДважды(t *testing.T) {
	catalogue, llm, understanding := стендПоиска()

	for i := 0; i < 3; i++ {
		SearchCatalog(catalogue, understanding.Understand("мышь подешевле"))
	}

	if llm.calls != 1 {
		t.Fatalf("походов к модели %d", llm.calls)
	}
}

// Пустая выдача с ошибкой — худшее, что можно показать покупателю.
func TestПровайдерЛежитИщемКакОбычно(t *testing.T) {
	catalogue, llm, understanding := стендПоиска()
	llm.broken = true

	found := SearchCatalog(catalogue, understanding.Understand("мышь"))

	if len(found) != 3 {
		t.Fatalf("нашлось %v, ожидались все три мыши", названия(found))
	}
}

func TestМодельОтветилаМусором(t *testing.T) {
	catalogue, llm, understanding := стендПоиска()
	llm.answer = "конечно! вот ваш ответ: мышь дешевле 2000"

	found := SearchCatalog(catalogue, understanding.Understand("клавиатура"))

	if len(found) != 1 || found[0].Title != "Механическая клавиатура" {
		t.Fatalf("нашлось %v", названия(found))
	}
}

func TestБезПровайдераОбычныйТекстовыйПоиск(t *testing.T) {
	understanding := NewQueryUnderstanding(nil)

	filters := understanding.Understand("недорогая беспроводная мышь")

	if filters.Text != "недорогая беспроводная мышь" || filters.HasMaxPrice || filters.InStockOnly {
		t.Fatalf("фильтры %+v", filters)
	}
}
