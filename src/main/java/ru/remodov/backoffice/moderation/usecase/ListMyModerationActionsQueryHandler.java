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
public class ListMyModerationActionsQueryHandler
    implements UseCaseHandler<ListMyModerationActionsQuery, ModerationActionPage> {

    private final ModerationActionRepository repo;

    @Override
    public Class<ListMyModerationActionsQuery> useCaseType() {
        return ListMyModerationActionsQuery.class;
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationActionPage handle(ListMyModerationActionsQuery q) {
        List<ModerationActionsPojo> rows = repo.findByModerator(
            q.moderatorId(), q.from(), q.to(), q.page(), q.size());
        long total = repo.countByModerator(q.moderatorId(), q.from(), q.to());
        List<ModerationActionView> views = rows.stream().map(this::toView).toList();
        return ModerationActionPage.of(views, q.page(), q.size(), total);
    }

    private ModerationActionView toView(ModerationActionsPojo p) {
        return new ModerationActionView(
            p.getId(), p.getProductId(), p.getModeratorId(),
            p.getReason(), p.getNote(), p.getDecidedAt());
    }
}
