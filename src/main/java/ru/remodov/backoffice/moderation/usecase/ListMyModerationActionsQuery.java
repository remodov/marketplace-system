package ru.remodov.backoffice.moderation.usecase;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

public record ListMyModerationActionsQuery(
    UUID moderatorId,
    OffsetDateTime from,
    OffsetDateTime to,
    int page,
    int size
) implements UseCaseQuery<ModerationActionPage> {

    public ListMyModerationActionsQuery {
        Objects.requireNonNull(moderatorId, "moderatorId");
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1, was: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be in [1, 100], was: " + size);
        }
    }
}
