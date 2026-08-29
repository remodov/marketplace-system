package ru.vikulinva.notificationservice.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.noCondition;
import static ru.vikulinva.notificationservice.generated.Tables.NOTIFICATIONS;

/**
 * Репозиторий для {@code notifications}. Только jOOQ DSL поверх
 * сгенерённых таблиц / POJO / enum'ов.
 */
@Repository
public class NotificationRepository {

    private final DSLContext dsl;

    public NotificationRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    public void insert(NotificationsPojo n) {
        dsl.insertInto(NOTIFICATIONS)
            .set(dsl.newRecord(NOTIFICATIONS, n))
            .execute();
    }

    public Optional<NotificationsPojo> findById(UUID id) {
        return Optional.ofNullable(
            dsl.selectFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.ID.eq(id))
                .fetchOneInto(NotificationsPojo.class));
    }

    public List<NotificationsPojo> findPending(int limit) {
        return dsl.selectFrom(NOTIFICATIONS)
            .where(NOTIFICATIONS.STATUS.eq(NotificationStatus.QUEUED))
            .orderBy(NOTIFICATIONS.CREATED_AT.asc())
            .limit(limit)
            .fetchInto(NotificationsPojo.class);
    }

    public Optional<NotificationsPojo> findByExternalId(String externalId) {
        return Optional.ofNullable(
            dsl.selectFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.EXTERNAL_ID.eq(externalId))
                .fetchOneInto(NotificationsPojo.class));
    }

    public List<NotificationsPojo> search(Filter filter, int page, int size) {
        return dsl.selectFrom(NOTIFICATIONS)
            .where(toCondition(filter))
            .orderBy(NOTIFICATIONS.CREATED_AT.desc())
            .limit(size)
            .offset((long) page * size)
            .fetchInto(NotificationsPojo.class);
    }

    public long count(Filter filter) {
        return dsl.fetchCount(dsl.selectOne().from(NOTIFICATIONS).where(toCondition(filter)));
    }

    public void updateStatus(UUID id,
                                NotificationStatus status,
                                OffsetDateTime sentAt,
                                String externalId,
                                String lastError) {
        var update = dsl.update(NOTIFICATIONS)
            .set(NOTIFICATIONS.STATUS, status)
            .set(NOTIFICATIONS.LAST_ERROR, lastError);
        if (sentAt != null) {
            update = update.set(NOTIFICATIONS.SENT_AT, sentAt);
        }
        if (externalId != null) {
            update = update.set(NOTIFICATIONS.EXTERNAL_ID, externalId);
        }
        update.where(NOTIFICATIONS.ID.eq(id)).execute();
    }

    public void markDelivered(UUID id, OffsetDateTime deliveredAt) {
        dsl.update(NOTIFICATIONS)
            .set(NOTIFICATIONS.STATUS, NotificationStatus.DELIVERED)
            .set(NOTIFICATIONS.DELIVERED_AT, deliveredAt)
            .where(NOTIFICATIONS.ID.eq(id))
            .and(NOTIFICATIONS.STATUS.eq(NotificationStatus.SENT))
            .execute();
    }

    public void markBounced(UUID id, String reason) {
        dsl.update(NOTIFICATIONS)
            .set(NOTIFICATIONS.STATUS, NotificationStatus.BOUNCED)
            .set(NOTIFICATIONS.LAST_ERROR, reason)
            .where(NOTIFICATIONS.ID.eq(id))
            .and(NOTIFICATIONS.STATUS.eq(NotificationStatus.SENT))
            .execute();
    }

    public int deleteOlderThan(OffsetDateTime threshold) {
        return dsl.deleteFrom(NOTIFICATIONS)
            .where(NOTIFICATIONS.CREATED_AT.lt(threshold))
            .execute();
    }

    private Condition toCondition(Filter filter) {
        Condition c = noCondition();
        if (filter.userId() != null) {
            c = c.and(NOTIFICATIONS.USER_ID.eq(filter.userId()));
        }
        if (filter.status() != null) {
            c = c.and(NOTIFICATIONS.STATUS.eq(filter.status()));
        }
        if (filter.channel() != null) {
            c = c.and(NOTIFICATIONS.CHANNEL.eq(filter.channel()));
        }
        if (filter.eventType() != null) {
            c = c.and(NOTIFICATIONS.EVENT_TYPE.eq(filter.eventType()));
        }
        if (filter.from() != null) {
            c = c.and(NOTIFICATIONS.CREATED_AT.ge(filter.from()));
        }
        if (filter.to() != null) {
            c = c.and(NOTIFICATIONS.CREATED_AT.lt(filter.to()));
        }
        return c;
    }

    public record Filter(
        UUID userId,
        NotificationStatus status,
        NotificationChannel channel,
        String eventType,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        public static Filter empty() { return new Filter(null, null, null, null, null, null); }
    }
}
