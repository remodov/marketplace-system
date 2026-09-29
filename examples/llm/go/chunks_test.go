package llm

import (
	"strings"
	"testing"
)

const doc = "Вернуть товар надлежащего качества можно в течение 14 дней с момента получения. " +
	"Товар должен сохранить товарный вид, упаковку и все комплектующие. " +
	"Деньги возвращаются тем же способом, которым была оплата, в течение десяти рабочих дней. " +
	"Товар с недостатком можно вернуть в течение гарантийного срока. " +
	"Доставку до склада в этом случае оплачивает магазин."

func lastWord(chunk string) string {
	parts := strings.Split(strings.TrimSpace(chunk), " ")
	return parts[len(parts)-1]
}

func TestНиОдинКусокНеДлиннееПотолка(t *testing.T) {
	for _, chunk := range Split(doc, 120, 30) {
		if length(chunk) > 120 {
			t.Fatalf("кусок длиной %d: %q", length(chunk), chunk)
		}
	}
}

// Перекрытие — единственное, что спасает предложение на стыке кусков. Без него
// вопрос про «десять рабочих дней» не найдёт ни один кусок.
func TestСоседниеКускиПерекрываются(t *testing.T) {
	chunks := Split(doc, 120, 30)

	if len(chunks) < 2 {
		t.Fatalf("ожидалось больше одного куска, получено %d", len(chunks))
	}
	if !strings.Contains(chunks[1], lastWord(chunks[0])) {
		t.Fatalf("второй кусок не содержит хвост первого: %q", chunks[1])
	}
}

func TestСловаНеРвутся(t *testing.T) {
	for _, chunk := range Split(doc, 120, 30) {
		if !strings.Contains(doc, lastWord(chunk)) {
			t.Fatalf("слово порвано: %q", lastWord(chunk))
		}
	}
}

func TestДлинноеПредложениеРежетсяПоСловам(t *testing.T) {
	long := strings.Repeat("слово ", 100) + "конец."

	chunks := Split(long, 100, 20)

	for _, chunk := range chunks {
		if length(chunk) > 100 {
			t.Fatalf("кусок длиной %d", length(chunk))
		}
	}
	if !strings.Contains(strings.Join(chunks, " "), "конец.") {
		t.Fatal("хвост длинного предложения потерялся")
	}
}

func TestПустойДокументДаётПустойСписок(t *testing.T) {
	if got := Split("   ", 100, 20); len(got) != 0 {
		t.Fatalf("ожидался пустой список, получено %v", got)
	}
}
