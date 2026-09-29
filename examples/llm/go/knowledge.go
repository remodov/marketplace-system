package llm

import "sort"

const (
	chunkChars   = 400
	chunkOverlap = 80
)

// Chunk — кусок документа: откуда взят, сам текст и его вектор.
type Chunk struct {
	Source string
	Text   string
	Vector []float64
}

// Match — найденный кусок и его близость к вопросу, от -1 до 1.
type Match struct {
	Chunk Chunk
	Score float64
}

// KnowledgeBase — векторная база в самом простом виде: срез в памяти и перебор
// всех векторов на каждый вопрос.
//
// На сотне документов так и надо: отдельная база добавит развёртывание,
// синхронизацию и ещё один повод упасть, а выигрыша не даст. Считать, когда пора
// переезжать, просто: перебор миллиона векторов по 256 чисел — это сотни
// миллионов операций на КАЖДЫЙ вопрос. Настоящая векторная база отвечает за
// миллисекунды приблизительным поиском (HNSW, IVF) — она не перебирает всё и
// сознательно иногда промахивается мимо точного ответа.
type KnowledgeBase struct {
	embed  Embed
	chunks []Chunk
}

func NewKnowledgeBase(embed Embed) *KnowledgeBase {
	return &KnowledgeBase{embed: embed}
}

// Index режет документ на куски и считает вектор каждого.
func (b *KnowledgeBase) Index(source, text string) {
	for _, piece := range Split(text, chunkChars, chunkOverlap) {
		b.chunks = append(b.chunks, Chunk{Source: source, Text: piece, Vector: b.embed(piece)})
	}
}

func (b *KnowledgeBase) Clear() { b.chunks = nil }

func (b *KnowledgeBase) Size() int { return len(b.chunks) }

// Search возвращает ближайшие к вопросу куски по убыванию близости.
//
// minScore — порог: всё, что ниже, не возвращается совсем. Без порога на любой
// вопрос находится «что-то», и помощник уверенно отвечает по случайному
// фрагменту.
func (b *KnowledgeBase) Search(question string, limit int, minScore float64) []Match {
	query := b.embed(question)
	var found []Match
	for _, chunk := range b.chunks {
		if score := Cosine(query, chunk.Vector); score >= minScore {
			found = append(found, Match{Chunk: chunk, Score: score})
		}
	}
	sort.SliceStable(found, func(i, j int) bool { return found[i].Score > found[j].Score })
	if len(found) > limit {
		found = found[:limit]
	}
	return found
}
