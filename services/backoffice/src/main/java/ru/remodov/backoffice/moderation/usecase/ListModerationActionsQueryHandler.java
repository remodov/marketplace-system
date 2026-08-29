package ru.remodov.backoffice.moderation.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.backoffice.generated.tables.pojos.ModerationActionsPojo;
import ru.remodov.backoffice.moderation.repository.ModerationActionRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class ListModerationActionsQueryHandler
    implements UseCaseHandler<ListModerationActionsQuery, ModerationActionPage> {

    private final ModerationActionRepository repo;

    @Override
    public Class<ListModerationActionsQuery> useCaseType() {
        return ListModerationActionsQuery.class;
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationActionPage handle(ListModerationActionsQuery q) {
        List<ModerationActionsPojo> rows = repo.findFiltered(
            q.moderatorId(), q.from(), q.to(), q.reason(), q.page(), q.size());
        long total = repo.countFiltered(q.moderatorId(), q.from(), q.to(), q.reason());
        List<ModerationActionView> views = rows.stream().map(this::toView).toList();
        return ModerationActionPage.of(views, q.page(), q.size(), total);
    }

    private ModerationActionView toView(ModerationActionsPojo p) {
        return new ModerationActionView(
            p.getId(), p.getProductId(), p.getModeratorId(),
            p.getReason(), p.getNote(), p.getDecidedAt());
    }
}
