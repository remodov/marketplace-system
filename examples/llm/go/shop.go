package llm

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// Product — карточка товара в том же виде, что в остальных версиях примера.
type Product struct {
	Title     string
	Price     int
	Available int
}

// Products — каталог для примеров и тестов.
func Products() []Product {
	return []Product{
		{"Беспроводная мышь", 1990, 5},
		{"Мышь игровая премиум", 7900, 3},
		{"Мышь офисная", 890, 0},
		{"Механическая клавиатура", 5400, 2},
	}
}

// FillKnowledge собирает базу знаний заново: сначала правила магазина, потом
// карточки товаров.
//
// Товары попадают сюда намеренно. Половина вопросов покупателя — не про правила,
// а про ассортимент, и отвечать на них по общим документам нечем.
func FillKnowledge(base *KnowledgeBase, docsDir string, products []Product) error {
	base.Clear()
	paths, err := filepath.Glob(filepath.Join(docsDir, "*.md"))
	if err != nil {
		return err
	}
	sort.Strings(paths)
	for _, path := range paths {
		text, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		base.Index(filepath.Base(path), string(text))
	}
	for _, product := range products {
		base.Index("каталог: "+product.Title, Card(product))
	}
	return nil
}

// Card — карточка кладётся фразой, а не полями через запятую: вопрос задают
// словами, и искать придётся по словам.
func Card(product Product) string {
	stock := "Нет в наличии."
	if product.Available > 0 {
		stock = fmt.Sprintf("В наличии %d штук.", product.Available)
	}
	return fmt.Sprintf("Товар «%s». Цена %d рублей. %s", product.Title, product.Price, stock)
}

// CatalogTools — инструменты агента поверх каталога.
//
// Важное здесь не код, а то, что агенту НЕ дано: сменить цену, удалить товар,
// выполнить произвольный запрос. Набор инструментов — это и есть граница его
// полномочий, и сужать её надёжнее, чем просить в подсказке «ничего не ломай».
// Скидку, например, агент придумает охотно, а отвечать за неё будет магазин.
func CatalogTools(products []Product) []Tool {
	find := func(args map[string]any) string {
		query := strings.ToLower(strings.TrimSpace(text(args, "query")))
		if query == "" {
			return "нужен аргумент query"
		}
		var lines []string
		for _, p := range products {
			if strings.Contains(strings.ToLower(p.Title), query) {
				lines = append(lines, fmt.Sprintf("%s | %d руб | доступно %d", p.Title, p.Price, p.Available))
			}
		}
		if len(lines) == 0 {
			return "ничего не найдено по запросу " + query
		}
		return strings.Join(lines, "\n")
	}

	stock := func(args map[string]any) string {
		title := strings.ToLower(strings.TrimSpace(text(args, "title")))
		for _, p := range products {
			if strings.ToLower(p.Title) == title {
				return fmt.Sprintf("%s: доступно %d", p.Title, p.Available)
			}
		}
		return "товара с таким названием нет"
	}

	reserve := func(args map[string]any) string {
		title := strings.ToLower(strings.TrimSpace(text(args, "title")))
		quantity := 1
		if q, ok := args["quantity"].(float64); ok {
			quantity = int(q)
		}
		for i := range products {
			if strings.ToLower(products[i].Title) == title {
				if quantity > products[i].Available {
					return "столько нет в наличии"
				}
				products[i].Available -= quantity
				return fmt.Sprintf("зарезервировано, осталось доступно %d", products[i].Available)
			}
		}
		return "товара с таким названием нет"
	}

	return []Tool{
		{Name: "find_products", Description: `найти товары по части названия; аргументы: {"query": "мышь"}`, Run: find},
		{Name: "stock", Description: `остаток товара по названию; аргументы: {"title": "Беспроводная мышь"}`, Run: stock},
		{Name: "reserve", Description: `зарезервировать товар; аргументы: {"title": "Беспроводная мышь", "quantity": 1}`,
			Mutating: true, Run: reserve},
	}
}

func text(args map[string]any, field string) string {
	if args == nil {
		return ""
	}
	value, _ := args[field].(string)
	return value
}
