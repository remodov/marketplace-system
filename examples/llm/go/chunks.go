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

	// TODO Б3: нарезка по границам предложений с перекрытием.
	// Один кусок не длиннее maxChars, слова не рвутся, хвост предыдущего куска
	// повторяется в начале следующего, предложение длиннее потолка режется по словам.
	clean := strings.TrimSpace(spaces.ReplaceAllString(text, " "))
	if clean == "" {
		return nil
	}
	return []string{clean}
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
