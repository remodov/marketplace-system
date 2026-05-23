package ru.remodov.backoffice.moderation.usecase;

import java.time.OffsetDateTime;
import java.util.UUID;
import ru.remodov.backoffice.generated.enums.ModerationReason;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

public record ListModerationActionsQuery(
    UUID moderatorId,
    OffsetDateTime from,
    OffsetDateTime to,
    ModerationReason reason,
    int page,
    int size
) implements UseCaseQuery<ModerationActionPage> {

    public ListModerationActionsQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1, was: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be in [1, 100], was: " + size);
        }
    }
}
