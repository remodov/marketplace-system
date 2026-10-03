package llm

import "strings"

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
	// TODO Б2: превратить фразу в фильтры.
	// Три вещи обязательны, иначе это нельзя выпускать: одна и та же фраза не
	// должна ходить к модели дважды; лежащий провайдер не должен ломать поиск;
	// ответ модели разбирается оборонительно, она вернёт не то, что обещала,
	// ровно тогда, когда этого не ждут.
	return PlainFilters(query)
}

func parseFilters(answer, original string) SearchFilters {
	// TODO Б2: разбор ответа модели. Поля описаны в подсказке, но обещание
	// модели не гарантия: она отвечает текстом, а не типом.
	return PlainFilters(original)
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
