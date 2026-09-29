package ru.vikulinva.catalogstarter.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * Нарезка документа на куски.
 *
 * <p>Зачем вообще резать: в подсказку модели влезает ограниченное число токенов,
 * а искать надо по смыслу небольшого фрагмента. Целая страница правил — один
 * вектор «про всё сразу», по нему ничего не находится.
 *
 * <p>Два параметра, которые решают всё. Размер куска: слишком мелкий теряет
 * контекст («до 14 дней» без того, о чём идёт речь), слишком крупный размывает
 * смысл. Перекрытие: без него предложение, попавшее на стык, теряется — ни в
 * одном куске оно не целое.
 */
public final class Chunks {

    private Chunks() {
    }

    /**
     * Режет по границам предложений, не разрывая слова, и повторяет хвост
     * предыдущего куска в начале следующего.
     *
     * @param maxChars  потолок длины куска
     * @param overlap   сколько символов хвоста повторить в следующем куске
     */
    public static List<String> split(String text, int maxChars, int overlap) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("размер куска должен быть больше нуля");
        }
        if (overlap < 0 || overlap >= maxChars) {
            throw new IllegalArgumentException("перекрытие должно быть меньше размера куска");
        }
        String clean = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return List.of();
        }

        List<String> sentences = sentences(clean);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (!current.isEmpty() && current.length() + 1 + sentence.length() > maxChars) {
                chunks.add(current.toString());
                /* Перекрытие берём только в тот запас, что остался под само
                   предложение: иначе хвост предыдущего куска вместе с длинным
                   предложением снова вылезет за потолок. */
                int room = maxChars - sentence.length() - 1;
                current = new StringBuilder(room <= 0 ? "" : tail(current.toString(), Math.min(overlap, room)));
            }
            /* Одно предложение длиннее потолка — режем по словам: иначе кусок
               вырастет за лимит и подсказка перестанет влезать в окно. */
            while (sentence.length() > maxChars) {
                int cut = sentence.lastIndexOf(' ', maxChars);
                if (cut <= 0) {
                    cut = maxChars;
                }
                chunks.add(sentence.substring(0, cut).strip());
                sentence = tail(sentence.substring(0, cut), overlap) + sentence.substring(cut);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks.stream().map(String::strip).filter(s -> !s.isBlank()).toList();
    }

    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean end = c == '.' || c == '!' || c == '?';
            if (end && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
                out.add(text.substring(start, i + 1).strip());
                start = i + 1;
            }
        }
        if (start < text.length()) {
            out.add(text.substring(start).strip());
        }
        return out.stream().filter(s -> !s.isBlank()).toList();
    }

    /** Хвост куска, обрезанный по границе слова, — он же начало следующего. */
    private static String tail(String chunk, int overlap) {
        if (overlap == 0 || chunk.length() <= overlap) {
            return overlap == 0 ? "" : chunk;
        }
        String piece = chunk.substring(chunk.length() - overlap);
        int space = piece.indexOf(' ');
        return space < 0 ? piece : piece.substring(space + 1);
    }
}
