package ru.remodov.backoffice.moderation.usecase;

import java.time.OffsetDateTime;
import java.util.UUID;
import ru.remodov.backoffice.generated.enums.ModerationReason;

public record ModerationActionView(
    UUID id,
    UUID productId,
    UUID moderatorId,
    ModerationReason reason,
    String note,
    OffsetDateTime decidedAt
) {
}
