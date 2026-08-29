package ru.remodov.catalog.domain;

public enum ProductSortField {
    CREATED_AT_DESC, CREATED_AT_ASC,
    UPDATED_AT_DESC, UPDATED_AT_ASC,
    TITLE_ASC, TITLE_DESC;

    public static ProductSortField parse(String raw) {
        if (raw == null || raw.isBlank()) return CREATED_AT_DESC;
        return switch (raw.toLowerCase()) {
            case "createdat,asc" -> CREATED_AT_ASC;
            case "createdat,desc" -> CREATED_AT_DESC;
            case "updatedat,asc" -> UPDATED_AT_ASC;
            case "updatedat,desc" -> UPDATED_AT_DESC;
            case "title,asc" -> TITLE_ASC;
            case "title,desc" -> TITLE_DESC;
            default -> CREATED_AT_DESC;
        };
    }
}
