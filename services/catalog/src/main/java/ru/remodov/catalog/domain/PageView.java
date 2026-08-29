package ru.remodov.catalog.domain;

import java.util.List;

public record PageView<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
