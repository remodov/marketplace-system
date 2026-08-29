package ru.remodov.catalog.repository;

import ru.remodov.catalog.generated.tables.pojos.CatalogAuditLogPojo;

public interface AuditLogRepository {
    void insert(CatalogAuditLogPojo entry);
}
