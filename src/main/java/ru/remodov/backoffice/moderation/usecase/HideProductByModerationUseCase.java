package ru.remodov.backoffice.moderation.usecase;

import java.util.Objects;
import java.util.UUID;
import ru.remodov.backoffice.generated.enums.ModerationReason;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record HideProductByModerationUseCase(
    UUID requestId,
    UUID productId,
    UUID moderatorId,
    ModerationReason reason,
    String note
) implements UseCaseCommand<ModerationActionView> {

    public HideProductByModerationUseCase {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(moderatorId, "moderatorId");
    }
}
