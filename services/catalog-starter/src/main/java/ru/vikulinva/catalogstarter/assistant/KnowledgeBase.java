package ru.vikulinva.catalogstarter.assistant;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Куски документов вместе с их векторами и поиск по близости.
 *
 * <p>Это векторная база в самом простом виде: список в памяти и перебор всех
 * векторов на каждый вопрос. На сотне документов так и надо — отдельная база
 * добавит развёртывание, синхронизацию и ещё один повод упасть, а выигрыша не
 * даст. Считать, когда пора переезжать, просто: перебор миллиона векторов по
 * 256 чисел — это сотни миллионов операций на КАЖДЫЙ вопрос.
 *
 * <p>Что даёт настоящая векторная база: приблизительный поиск (HNSW, IVF) —
 * он отвечает за миллисекунды, потому что не перебирает всё, и сознательно
 * иногда промахивается мимо точного ответа.
 */
@Component
public class KnowledgeBase {

    /** Кусок документа: откуда взят, сам текст и его вектор. */
    public record Chunk(String source, String text, float[] vector) {
    }

    /** Найденный кусок и его близость к вопросу, от -1 до 1. */
    public record Match(Chunk chunk, double score) {
    }

    private final Embeddings embeddings;
    private final List<Chunk> chunks = new CopyOnWriteArrayList<>();

    public KnowledgeBase(Embeddings embeddings) {
        this.embeddings = embeddings;
    }

    /** Режет документ на куски и считает вектор каждого. */
    public void index(String source, String text) {
        for (String piece : Chunks.split(text, 400, 80)) {
            chunks.add(new Chunk(source, piece, embeddings.embed(piece)));
        }
    }

    public void clear() {
        chunks.clear();
    }

    public int size() {
        return chunks.size();
    }

    /**
     * Ближайшие к вопросу куски, отсортированные по убыванию близости.
     *
     * @param minScore порог: всё, что ниже, не возвращается совсем. Без порога
     *                 на любой вопрос находится «что-то», и помощник уверенно
     *                 отвечает по случайному фрагменту.
     */
    public List<Match> search(String question, int limit, double minScore) {
        float[] query = embeddings.embed(question);
        List<Match> found = new ArrayList<>();
        for (Chunk chunk : chunks) {
            double score = cosine(query, chunk.vector());
            if (score >= minScore) {
                found.add(new Match(chunk, score));
            }
        }
        found.sort(Comparator.comparingDouble(Match::score).reversed());
        return found.size() > limit ? List.copyOf(found.subList(0, limit)) : List.copyOf(found);
    }

    /* Векторы нормированы, поэтому косинус — это просто скалярное произведение. */
    static double cosine(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
