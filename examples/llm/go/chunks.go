// Package llm — те же два урока, что в Java-версии практикума: помощник,
// отвечающий по документам магазина, и агент с инструментами. Только
// стандартная библиотека: ни зависимостей, ни ключа, ни интернета.
package llm

import (
	"regexp"
	"strings"
)

var (
	spaces      = regexp.MustCompile(`\s+`)
	sentenceEnd = regexp.MustCompile(`([.!?])\s+`)
)

// Split режет документ на куски по границам предложений, не разрывая слова, и
// повторяет хвост предыдущего куска в начале следующего.
//
// Зачем вообще резать: в подсказку модели влезает ограниченное число токенов, а
// искать надо по смыслу небольшого фрагмента. Целая страница правил — один
// вектор «про всё сразу», по нему не находится ничего.
//
// Два параметра решают всё. Размер куска: слишком мелкий теряет контекст, слишком
// крупный размывает смысл. Перекрытие: без него предложение, попавшее на стык,
// теряется — ни в одном куске оно не целое.
func Split(text string, maxChars, overlap int) []string {
	if maxChars <= 0 {
		panic("размер куска должен быть больше нуля")
	}
	if overlap < 0 || overlap >= maxChars {
		panic("перекрытие должно быть меньше размера куска")
	}

	clean := strings.TrimSpace(spaces.ReplaceAllString(text, " "))
	if clean == "" {
		return nil
	}

	var chunks []string
	current := ""
	for _, sentence := range sentences(clean) {
		if current != "" && length(current)+1+length(sentence) > maxChars {
			chunks = append(chunks, current)
			// Перекрытие берём только в тот запас, что остался под само
			// предложение: иначе хвост вместе с длинным предложением снова
			// вылезет за потолок.
			room := maxChars - length(sentence) - 1
			if room > 0 {
				current = tail(current, min(overlap, room))
			} else {
				current = ""
			}
		}

		// Одно предложение длиннее потолка — режем по словам: выбрасывать его
		// нельзя, а оставить целым не даёт окно модели.
		for length(sentence) > maxChars {
			cut := cutAtWord(sentence, maxChars)
			chunks = append(chunks, strings.TrimSpace(slice(sentence, 0, cut)))
			sentence = tail(slice(sentence, 0, cut), overlap) + slice(sentence, cut, length(sentence))
		}

		if current == "" {
			current = sentence
		} else {
			current = current + " " + sentence
		}
	}
	if current != "" {
		chunks = append(chunks, current)
	}

	var out []string
	for _, c := range chunks {
		if t := strings.TrimSpace(c); t != "" {
			out = append(out, t)
		}
	}
	return out
}

func sentences(text string) []string {
	разбито := sentenceEnd.ReplaceAllString(text, "$1\n")
	var out []string
	for _, s := range strings.Split(разбито, "\n") {
		if t := strings.TrimSpace(s); t != "" {
			out = append(out, t)
		}
	}
	return out
}

// tail — конец куска, обрезанный по границе слова; он же начало следующего.
func tail(chunk string, overlap int) string {
	if overlap <= 0 {
		return ""
	}
	if length(chunk) <= overlap {
		return chunk
	}
	piece := slice(chunk, length(chunk)-overlap, length(chunk))
	if space := strings.Index(piece, " "); space >= 0 {
		return piece[space+1:]
	}
	return piece
}

// Длина и срез считаются в рунах: в байтах кириллическая буква занимает две, и
// потолок куска оказался бы вдвое меньше обещанного.
func length(s string) int { return len([]rune(s)) }

func slice(s string, from, to int) string { return string([]rune(s)[from:to]) }

func cutAtWord(sentence string, maxChars int) int {
	runes := []rune(sentence)
	for i := maxChars; i > 0; i-- {
		if runes[i] == ' ' {
			return i
		}
	}
	return maxChars
}
