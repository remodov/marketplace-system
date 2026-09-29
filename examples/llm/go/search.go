package llm

import (
	"encoding/json"
	"fmt"
	"strings"
)

// searchPrompt — промпт тоже код: его меняют, и поведение поиска меняется вместе
// с ним. Значит, и проверять его надо как код.
const searchPrompt = `Разбери запрос покупателя интернет-магазина в JSON без пояснений.
Поля: text — что искать по названию, maxPrice — потолок цены в рублях или null,
inStockOnly — true, если покупателю нужен товар в наличии.
Запрос: %s
`

// SearchFilters — во что превращается фраза покупателя: текст для поиска по
// названию и, если из фразы это следует, потолок цены и требование наличия.
type SearchFilters struct {
	Text        string
	MaxPrice    int
	HasMaxPrice bool
	InStockOnly bool
}

// PlainFilters — фраза как есть, без разбора. Сюда приходят, когда провайдера
// нет или он ответил не тем.
func PlainFilters(query string) SearchFilters {
	return SearchFilters{Text: query}
}

// QueryUnderstanding превращает фразу покупателя в фильтры каталога.
//
// Покупатель пишет не «мышь», а «недорогая беспроводная мышь в наличии».
// Обычный поиск по названию на такой фразе не найдёт ничего: слов «недорогая» и
// «в наличии» в названиях нет.
//
// Три вещи обязательны, без них это нельзя выпускать: кэш (одна и та же фраза
// повторяется чаще, чем кажется, а каждый поход к модели стоит денег и сотен
// миллисекунд), работа без модели (провайдер лежит — поиск обязан продолжать
// работать как обычный текстовый) и оборонительный разбор ответа (модель
// отвечает текстом, а не типом).
type QueryUnderstanding struct {
	llm   Complete
	cache map[string]SearchFilters
}

func NewQueryUnderstanding(llm Complete) *QueryUnderstanding {
	return &QueryUnderstanding{llm: llm, cache: map[string]SearchFilters{}}
}

func (q *QueryUnderstanding) Understand(query string) SearchFilters {
	if cached, ok := q.cache[query]; ok {
		return cached
	}
	filters := q.ask(query)
	// Кэш здесь самый простой, какой бывает. В настоящем сервисе у него обязаны
	// быть срок жизни и потолок размера: карта растёт, пока не кончится память,
	// а ответ модели на вчерашний ассортимент устаревает.
	q.cache[query] = filters
	return filters
}

func (q *QueryUnderstanding) ask(query string) SearchFilters {
	if q.llm == nil {
		return PlainFilters(query)
	}
	answer, err := q.llm(fmt.Sprintf(searchPrompt, query))
	if err != nil {
		// Ошибку покупателю не показываем: он спрашивал про мышь, а не про наш
		// провайдер.
		return PlainFilters(query)
	}
	return parseFilters(answer, query)
}

// parseFilters — обещание модели не гарантия: она отвечает текстом, а не типом.
func parseFilters(answer, original string) SearchFilters {
	var raw struct {
		Text        *string  `json:"text"`
		MaxPrice    *float64 `json:"maxPrice"`
		InStockOnly *bool    `json:"inStockOnly"`
	}
	if err := json.Unmarshal([]byte(strings.TrimSpace(answer)), &raw); err != nil {
		return PlainFilters(original)
	}

	filters := SearchFilters{Text: original}
	if raw.Text != nil && strings.TrimSpace(*raw.Text) != "" {
		filters.Text = strings.TrimSpace(*raw.Text)
	}
	if raw.MaxPrice != nil {
		filters.MaxPrice = int(*raw.MaxPrice)
		filters.HasMaxPrice = true
	}
	if raw.InStockOnly != nil {
		filters.InStockOnly = *raw.InStockOnly
	}
	return filters
}

// SearchCatalog применяет фильтры обычным перебором — модель тут больше не
// участвует, и это важно: она разбирает запрос, а не ищет.
func SearchCatalog(products []Product, filters SearchFilters) []Product {
	text := strings.ToLower(strings.TrimSpace(filters.Text))
	var found []Product
	for _, p := range products {
		if text != "" && !strings.Contains(strings.ToLower(p.Title), text) {
			continue
		}
		if filters.HasMaxPrice && p.Price > filters.MaxPrice {
			continue
		}
		if filters.InStockOnly && p.Available <= 0 {
			continue
		}
		found = append(found, p)
	}
	return found
}
