package ru.remodov.backoffice.moderation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.remodov.backoffice.generated.enums.ModerationReason;
import ru.remodov.backoffice.generated.tables.pojos.ModerationActionsPojo;

public interface ModerationActionRepository {

    Optional<ModerationActionsPojo> findByRequestId(UUID requestId);

    void insert(ModerationActionsPojo action);

    List<ModerationActionsPojo> findByModerator(UUID moderatorId,
                                                OffsetDateTime from,
                                                OffsetDateTime to,
                                                int page,
                                                int size);

    long countByModerator(UUID moderatorId, OffsetDateTime from, OffsetDateTime to);

    List<ModerationActionsPojo> findFiltered(UUID moderatorId,
                                             OffsetDateTime from,
                                             OffsetDateTime to,
                                             ModerationReason reason,
                                             int page,
                                             int size);

    long countFiltered(UUID moderatorId,
                       OffsetDateTime from,
                       OffsetDateTime to,
                       ModerationReason reason);
}
