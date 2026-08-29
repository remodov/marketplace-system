package ru.remodov.backoffice.moderation.repository;

import static ru.remodov.backoffice.generated.Tables.MODERATION_ACTIONS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.remodov.backoffice.generated.enums.ModerationReason;
import ru.remodov.backoffice.generated.tables.pojos.ModerationActionsPojo;

@Repository
@RequiredArgsConstructor
public class JooqModerationActionRepository implements ModerationActionRepository {

    private final DSLContext dsl;

    @Override
    public Optional<ModerationActionsPojo> findByRequestId(UUID requestId) {
        return dsl.selectFrom(MODERATION_ACTIONS)
            .where(MODERATION_ACTIONS.REQUEST_ID.eq(requestId))
            .fetchOptionalInto(ModerationActionsPojo.class);
    }

    @Override
    public void insert(ModerationActionsPojo a) {
        dsl.insertInto(MODERATION_ACTIONS)
            .set(MODERATION_ACTIONS.ID, a.getId())
            .set(MODERATION_ACTIONS.PRODUCT_ID, a.getProductId())
            .set(MODERATION_ACTIONS.MODERATOR_ID, a.getModeratorId())
            .set(MODERATION_ACTIONS.REASON, a.getReason())
            .set(MODERATION_ACTIONS.NOTE, a.getNote())
            .set(MODERATION_ACTIONS.DECIDED_AT, a.getDecidedAt())
            .set(MODERATION_ACTIONS.REQUEST_ID, a.getRequestId())
            .execute();
    }

    @Override
    public List<ModerationActionsPojo> findByModerator(UUID moderatorId,
                                                       OffsetDateTime from,
                                                       OffsetDateTime to,
                                                       int page,
                                                       int size) {
        return fetchPage(moderatorFilter(moderatorId, from, to), page, size);
    }

    @Override
    public long countByModerator(UUID moderatorId, OffsetDateTime from, OffsetDateTime to) {
        return count(moderatorFilter(moderatorId, from, to));
    }

    @Override
    public List<ModerationActionsPojo> findFiltered(UUID moderatorId,
                                                    OffsetDateTime from,
                                                    OffsetDateTime to,
                                                    ModerationReason reason,
                                                    int page,
                                                    int size) {
        return fetchPage(adminFilter(moderatorId, from, to, reason), page, size);
    }

    @Override
    public long countFiltered(UUID moderatorId,
                              OffsetDateTime from,
                              OffsetDateTime to,
                              ModerationReason reason) {
        return count(adminFilter(moderatorId, from, to, reason));
    }

    private List<ModerationActionsPojo> fetchPage(Condition where, int page, int size) {
        return dsl.selectFrom(MODERATION_ACTIONS)
            .where(where)
            .orderBy(MODERATION_ACTIONS.DECIDED_AT.desc())
            .limit(size)
            .offset((long) (page - 1) * size)
            .fetchInto(ModerationActionsPojo.class);
    }

    private long count(Condition where) {
        Integer count = dsl.selectCount()
            .from(MODERATION_ACTIONS)
            .where(where)
            .fetchOne(0, Integer.class);
        return count == null ? 0L : count.longValue();
    }

    private Condition moderatorFilter(UUID moderatorId, OffsetDateTime from, OffsetDateTime to) {
        Condition c = MODERATION_ACTIONS.MODERATOR_ID.eq(moderatorId);
        c = withDateRange(c, from, to);
        return c;
    }

    private Condition adminFilter(UUID moderatorId,
                                  OffsetDateTime from,
                                  OffsetDateTime to,
                                  ModerationReason reason) {
        Condition c = DSL.noCondition();
        if (moderatorId != null) {
            c = c.and(MODERATION_ACTIONS.MODERATOR_ID.eq(moderatorId));
        }
        c = withDateRange(c, from, to);
        if (reason != null) {
            c = c.and(MODERATION_ACTIONS.REASON.eq(reason));
        }
        return c;
    }

    private Condition withDateRange(Condition c, OffsetDateTime from, OffsetDateTime to) {
        if (from != null) {
            c = c.and(MODERATION_ACTIONS.DECIDED_AT.ge(from));
        }
        if (to != null) {
            c = c.and(MODERATION_ACTIONS.DECIDED_AT.lt(to));
        }
        return c;
    }
}
