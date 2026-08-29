package ru.vikulinva.orderservice.usecase.query.dto;

import java.util.List;
import java.util.Objects;

/**
 * Результат страницы. {@code total} — общее число записей по фильтру;
 * {@code page} — номер страницы (с 0); {@code size} — запрошенный размер.
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public PageResult {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("page must be ≥ 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be ≥ 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be ≥ 0");
        }
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < total;
    }
}
