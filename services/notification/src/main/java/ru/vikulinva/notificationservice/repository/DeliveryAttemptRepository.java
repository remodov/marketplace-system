package ru.vikulinva.notificationservice.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.vikulinva.notificationservice.generated.tables.pojos.DeliveryAttemptsPojo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static ru.vikulinva.notificationservice.generated.Tables.DELIVERY_ATTEMPTS;

@Repository
public class DeliveryAttemptRepository {

    private final DSLContext dsl;

    public DeliveryAttemptRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    public void insert(DeliveryAttemptsPojo a) {
        dsl.insertInto(DELIVERY_ATTEMPTS)
            .set(dsl.newRecord(DELIVERY_ATTEMPTS, a))
            .execute();
    }

    public int countAttempts(UUID notificationId) {
        return dsl.fetchCount(
            dsl.selectOne()
                .from(DELIVERY_ATTEMPTS)
                .where(DELIVERY_ATTEMPTS.NOTIFICATION_ID.eq(notificationId)));
    }

    public List<DeliveryAttemptsPojo> findByNotificationId(UUID notificationId) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
            .where(DELIVERY_ATTEMPTS.NOTIFICATION_ID.eq(notificationId))
            .orderBy(DELIVERY_ATTEMPTS.ATTEMPT_NUMBER.asc())
            .fetchInto(DeliveryAttemptsPojo.class);
    }
}
