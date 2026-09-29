// Команда scores показывает, какие числа даёт поиск по близости.
//
// Запуск: go run ./scores
//
// Тут и видно, почему порог llm.MinScore стоит там, где стоит: вопросы по делу
// дают заметно больше, а вопрос не по теме проваливается почти в ноль. Уберите
// фильтр stop в embeddings.go и запустите снова — вопрос про масло в двигателе
// начнёт находить правила возврата.
package main

import (
	"fmt"
	"log"
	"path/filepath"

	llm "github.com/remodov/marketplace-system/examples/llm/go"
)

var questions = []string{
	"сколько дней на возврат товара",
	"когда вернут деньги за возврат",
	"сколько стоит доставка курьером",
	"есть ли беспроводная мышь и сколько стоит",
	"какая гарантия на электронику",
	"как поменять масло в двигателе автомобиля",
}

func main() {
	base := llm.NewKnowledgeBase(llm.HashingEmbed)
	if err := llm.FillKnowledge(base, filepath.Join("..", "knowledge"), llm.Products()); err != nil {
		log.Fatal(err)
	}
	fmt.Printf("кусков в базе: %d, порог: %.2f\n\n", base.Size(), llm.MinScore)
	for _, question := range questions {
		fmt.Println(question)
		for _, m := range base.Search(question, 3, -1) {
			mark := " "
			if m.Score < llm.MinScore {
				mark = "×"
			}
			text := []rune(m.Chunk.Text)
			if len(text) > 58 {
				text = text[:58]
			}
			fmt.Printf("  %s %.3f  %-28s %s\n", mark, m.Score, m.Chunk.Source, string(text))
		}
		fmt.Println()
	}
}
