package ru.remodov.backoffice.moderation.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface IdempotencyRecordRepository {

    void insert(UUID requestId, String responseHash, OffsetDateTime createdAt);
}
