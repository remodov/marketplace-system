package ru.remodov.backoffice.moderation.repository;

import static ru.remodov.backoffice.generated.Tables.IDEMPOTENCY_RECORDS;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JooqIdempotencyRecordRepository implements IdempotencyRecordRepository {

    private final DSLContext dsl;

    @Override
    public void insert(UUID requestId, String responseHash, OffsetDateTime createdAt) {
        dsl.insertInto(IDEMPOTENCY_RECORDS)
            .set(IDEMPOTENCY_RECORDS.REQUEST_ID, requestId)
            .set(IDEMPOTENCY_RECORDS.RESPONSE_HASH, responseHash)
            .set(IDEMPOTENCY_RECORDS.CREATED_AT, createdAt)
            .onConflictDoNothing()
            .execute();
    }
}
