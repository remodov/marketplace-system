package llm

import (
	"fmt"
	"strings"
)

// TopK — сколько кусков кладём в подсказку. Больше — дороже и хуже: лишние куски
// разбавляют нужный и уводят ответ в сторону.
const TopK = 3

// MinScore — порог близости. Не угадан, а измерен на этих документах: вопросы по
// делу дают заметно больше, вопрос не по теме проваливается почти в ноль. Со
// смысловой моделью эмбеддингов порог подбирают заново, глядя на выдачу.
const MinScore = 0.15

const ragPrompt = `Ты помощник интернет-магазина. Ответь на вопрос покупателя, опираясь
ТОЛЬКО на фрагменты ниже. Если ответа в них нет, так и скажи — не придумывай.
Отвечай коротко, двумя-тремя предложениями.

Фрагменты:
%s

Вопрос: %s
`

// Complete — порт к языковой модели. Ошибка означает, что провайдер недоступен.
type Complete func(prompt string) (string, error)

// Answer — ответ и куски, по которым он собран: без источников ответу нельзя верить.
type Answer struct {
	Text     string
	Sources  []string
	Grounded bool
}

// RagAssistant отвечает на вопрос покупателя по документам магазина.
//
// Схема целиком: вопрос → поиск похожих кусков → подсказка из найденного →
// ответ модели. Модель тут ничего не знает про магазин и не должна знать: всё,
// чем она располагает, лежит в подсказке. Это и есть смысл RAG — знание
// приносят из базы, а не из весов модели.
//
// Главное правило, из-за которого всё и затевалось: ничего не нашли — отвечаем
// «не знаю», а не зовём модель. Модель, которой не дали контекст, не молчит —
// она сочиняет правдоподобный ответ про сроки возврата, и по нему покупатель
// идёт к оператору. Отказ дешевле выдуманного ответа.
type RagAssistant struct {
	base *KnowledgeBase
	llm  Complete
}

// NewRagAssistant принимает llm == nil, если провайдера нет: поиск от этого
// работать не перестаёт.
func NewRagAssistant(base *KnowledgeBase, llm Complete) *RagAssistant {
	return &RagAssistant{base: base, llm: llm}
}

func (a *RagAssistant) Ask(question string) Answer {
	found := a.base.Search(question, TopK, MinScore)
	if len(found) == 0 {
		return Answer{Text: "Не нашёл ответа в документах магазина. " +
			"Спросите оператора — он посмотрит вручную."}
	}

	var sources []string
	seen := map[string]bool{}
	var context strings.Builder
	for _, m := range found {
		if !seen[m.Chunk.Source] {
			seen[m.Chunk.Source] = true
			sources = append(sources, m.Chunk.Source)
		}
		context.WriteString("- " + m.Chunk.Text + "\n")
	}

	if a.llm == nil {
		// Без провайдера отдаём найденное как есть. Половина пользы RAG — именно
		// в поиске: человек видит нужный абзац, даже когда сформулировать ответ
		// некому.
		return Answer{Text: found[0].Chunk.Text, Sources: sources, Grounded: true}
	}
	text, err := a.llm(fmt.Sprintf(ragPrompt, strings.TrimRight(context.String(), "\n"), question))
	if err != nil {
		return Answer{Text: found[0].Chunk.Text, Sources: sources, Grounded: true}
	}
	return Answer{Text: strings.TrimSpace(text), Sources: sources, Grounded: true}
}
