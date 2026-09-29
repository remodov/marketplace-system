package llm

import (
	"math"
	"regexp"
	"strings"
)

// Dimensions — длина вектора. Одна и та же для всех текстов.
const Dimensions = 256

// Embed — порт к модели эмбеддингов: текст превращается в вектор, близкие по
// смыслу тексты дают близкие векторы. Отдельный тип не ради красоты —
// провайдера меняют, он падает, а в тестах его подменяют.
type Embed func(text string) []float64

// stop — слова-связки выбрасываются. Без этого вопрос «как поменять масло в
// двигателе» находит правила возврата: общих «как», «в», «на» хватает, чтобы
// близость перевалила любой разумный порог, — и помощник уверенно отвечает не
// по делу.
var stop = map[string]bool{
	"как": true, "что": true, "это": true, "или": true, "для": true, "при": true,
	"над": true, "под": true, "без": true, "его": true, "все": true, "так": true,
	"уже": true, "там": true, "где": true, "они": true, "она": true, "оно": true,
	"мне": true, "нам": true, "вам": true, "чем": true, "если": true, "когда": true,
	"какой": true, "какая": true, "какие": true, "можно": true, "нужно": true,
	"есть": true, "быть": true,
}

var notWord = regexp.MustCompile(`[^\p{L}\p{N}]+`)

// HashingEmbed — локальная замена провайдера: слово раскладывается по корзинам
// вектора хэшем, вектор нормируется.
//
// Честно о границе: это не смысловые векторы. Хэш-эмбеддинг сближает тексты, у
// которых совпадают слова, и ничего не знает про синонимы. Настоящая модель
// сближает их по смыслу. Зачем он тогда нужен: весь остальной код — нарезка,
// поиск, сборка подсказки, отказ при отсутствии ответа — работает и проверяется
// без ключа, интернета и денег. Подключение провайдера меняет ровно эту функцию.
func HashingEmbed(text string) []float64 {
	vector := make([]float64, Dimensions)
	for _, word := range words(text) {
		h := fnv1a(word)
		// Знак от второго байта хэша: иначе все слова только прибавляют, и любые
		// два длинных текста оказываются похожими просто потому, что длинные.
		if (h>>16)&1 == 0 {
			vector[h%Dimensions]++
		} else {
			vector[h%Dimensions]--
		}
	}
	return normalize(vector)
}

// words — длинные слова обрезаются до шести букв, это грубая замена морфологии:
// «доставка» и «доставки» так попадают в одну корзину. На коротких словах приём
// не спасает: «товар» и «товара» так и останутся разными. Настоящей модели
// эмбеддингов такой костыль не нужен вовсе, она про падежи знает.
func words(text string) []string {
	var out []string
	for _, raw := range notWord.Split(strings.ToLower(text), -1) {
		runes := []rune(raw)
		if len(runes) <= 2 || stop[raw] {
			continue
		}
		if len(runes) > 6 {
			raw = string(runes[:6])
		}
		out = append(out, raw)
	}
	return out
}

func fnv1a(word string) uint32 {
	var h uint32 = 0x811C9DC5
	for _, b := range []byte(word) {
		h = (h ^ uint32(b)) * 0x01000193
	}
	return h
}

// normalize — без нормировки длинный текст «побеждает» короткий на любом вопросе.
func normalize(vector []float64) []float64 {
	var sum float64
	for _, v := range vector {
		sum += v * v
	}
	length := math.Sqrt(sum)
	if length == 0 {
		return vector
	}
	for i := range vector {
		vector[i] /= length
	}
	return vector
}

// Cosine — векторы нормированы, поэтому косинус это просто скалярное произведение.
func Cosine(a, b []float64) float64 {
	var sum float64
	for i := 0; i < len(a) && i < len(b); i++ {
		sum += a[i] * b[i]
	}
	return sum
}
