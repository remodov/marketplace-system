package ru.vikulinva.catalogstarter.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Нарезка на куски. Тест без Spring: резалка — чистая функция, и проверять её
 * поднятым контекстом незачем.
 */
class ChunksTest {

    private static final String DOC = """
        Вернуть товар надлежащего качества можно в течение 14 дней с момента получения.
        Товар должен сохранить товарный вид, упаковку и все комплектующие.
        Деньги возвращаются тем же способом, которым была оплата, в течение десяти рабочих дней.
        Товар с недостатком можно вернуть в течение гарантийного срока.
        Доставку до склада в этом случае оплачивает магазин.
        """;

    @Test
    @DisplayName("ни один кусок не длиннее потолка")
    void everyChunkFitsTheLimit() {
        List<String> chunks = Chunks.split(DOC, 120, 30);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(120));
    }

    /* Перекрытие — единственное, что спасает предложение на стыке кусков.
       Без него вопрос про «десять рабочих дней» не найдёт ни один кусок. */
    @Test
    @DisplayName("соседние куски перекрываются хвостом предыдущего")
    void neighboursOverlap() {
        List<String> chunks = Chunks.split(DOC, 120, 30);

        assertThat(chunks.size()).isGreaterThan(1);
        String tailOfFirst = lastWord(chunks.get(0));
        assertThat(chunks.get(1)).contains(tailOfFirst);
    }

    @Test
    @DisplayName("слова не рвутся посередине")
    void wordsAreNotCutInHalf() {
        List<String> chunks = Chunks.split(DOC, 120, 30);

        for (String chunk : chunks) {
            assertThat(DOC.replaceAll("\\s+", " ")).contains(lastWord(chunk));
        }
    }

    @Test
    @DisplayName("предложение длиннее потолка режется по словам, а не выбрасывается")
    void oneVeryLongSentenceIsSplit() {
        String long_ = "слово ".repeat(100) + "конец.";

        List<String> chunks = Chunks.split(long_, 100, 20);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
        assertThat(String.join(" ", chunks)).contains("конец.");
    }

    @Test
    @DisplayName("пустой документ даёт пустой список, а не кусок из пробелов")
    void emptyTextGivesNothing() {
        assertThat(Chunks.split("   ", 100, 20)).isEmpty();
        assertThat(Chunks.split(null, 100, 20)).isEmpty();
    }

    private static String lastWord(String chunk) {
        String[] words = chunk.strip().split(" ");
        return words[words.length - 1];
    }
}
