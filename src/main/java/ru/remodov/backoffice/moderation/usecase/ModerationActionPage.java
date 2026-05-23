package ru.remodov.backoffice.moderation.usecase;

import java.util.List;

public record ModerationActionPage(
    List<ModerationActionView> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public static ModerationActionPage of(List<ModerationActionView> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new ModerationActionPage(content, page, size, totalElements, totalPages);
    }
}
