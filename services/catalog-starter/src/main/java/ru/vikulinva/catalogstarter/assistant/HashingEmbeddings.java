package ru.vikulinva.catalogstarter.assistant;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Локальная замена провайдера эмбеддингов: слово раскладывается по корзинам
 * вектора хэшем, вектор нормируется.
 *
 * <p>Честно о границе: это не смысловые векторы. Хэш-эмбеддинг сближает тексты,
 * у которых совпадают слова, и ничего не знает про синонимы — «возврат» и
 * «вернуть покупку» для него разные вещи. Настоящая модель эмбеддингов сближает
 * их по смыслу.
 *
 * <p>Зачем он тогда нужен: весь остальной код — нарезка, поиск по близости,
 * сборка подсказки, отказ при отсутствии ответа — работает и проверяется без
 * ключа, интернета и денег. Настоящий провайдер подключается своим бином с
 * {@code @Primary} — остальной код не меняется вообще. Так и выглядит польза
 * от порта.
 */
@Component
public class HashingEmbeddings implements Embeddings {

    static final int DIMENSIONS = 256;

    /*
     * Слова-связки выбрасываются. Без этого вопрос «как поменять масло в
     * двигателе» находит правила возврата: общих «как», «в», «на» хватает,
     * чтобы близость перевалила любой разумный порог, — и помощник уверенно
     * отвечает не по делу. Проверено: отсев поднял разрыв между нужным
     * документом и случайным втрое.
     */
    private static final Set<String> STOP = Set.of(
        "как", "что", "это", "или", "для", "при", "над", "под", "без", "его", "все",
        "так", "уже", "там", "где", "они", "она", "оно", "мне", "нам", "вам", "чем",
        "если", "когда", "какой", "какая", "какие", "можно", "нужно", "есть", "быть");

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : words(text)) {
            int bucket = Math.floorMod(word.hashCode(), DIMENSIONS);
            /* Знак от второго хэша: иначе все слова только прибавляют, и любые
               два длинных текста оказываются похожими просто потому, что длинные. */
            vector[bucket] += (word.hashCode() >> 16 & 1) == 0 ? 1f : -1f;
        }
        return normalize(vector);
    }

    /*
     * Длинные слова обрезаются до шести букв — грубая замена морфологии:
     * «доставка» и «доставки» так попадают в одну корзину, а без этого вопрос
     * покупателя не совпал бы с документом ни одним словом. На коротких словах
     * приём не спасает: «товар» и «товара» так и останутся разными. Настоящей
     * модели эмбеддингов такой костыль не нужен вовсе, она про падежи знает.
     */
    static String[] words(String text) {
        if (text == null) {
            return new String[0];
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .filter(w -> w.length() > 2)
            .filter(w -> !STOP.contains(w))
            .map(w -> w.length() > 6 ? w.substring(0, 6) : w)
            .toArray(String[]::new);
    }

    /** Без нормировки длинный текст «побеждает» короткий на любом вопросе. */
    private static float[] normalize(float[] vector) {
        double length = 0;
        for (float v : vector) {
            length += v * v;
        }
        length = Math.sqrt(length);
        if (length == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) length;
        }
        return vector;
    }
}
