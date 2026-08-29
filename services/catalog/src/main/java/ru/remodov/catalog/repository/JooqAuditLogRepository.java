package ru.remodov.catalog.repository;

import static ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.remodov.catalog.generated.tables.pojos.CatalogAuditLogPojo;
import ru.remodov.catalog.generated.tables.records.CatalogAuditLogRecord;

@Repository
@RequiredArgsConstructor
public class JooqAuditLogRepository implements AuditLogRepository {

    private final DSLContext dsl;

    @Override
    public void insert(CatalogAuditLogPojo entry) {
        CatalogAuditLogRecord rec = dsl.newRecord(CATALOG_AUDIT_LOG, entry);
        rec.insert();
    }
}
