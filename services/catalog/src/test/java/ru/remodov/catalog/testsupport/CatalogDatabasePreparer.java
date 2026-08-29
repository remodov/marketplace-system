package ru.remodov.catalog.testsupport;

import static ru.remodov.catalog.generated.Tables.CATALOG_AUDIT_LOG;
import static ru.remodov.catalog.generated.Tables.PRODUCTS;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.remodov.catalog.generated.tables.pojos.ProductsPojo;

@Component
@RequiredArgsConstructor
public class CatalogDatabasePreparer {

    private final DSLContext dsl;
    private final List<Runnable> preparers = new ArrayList<>();

    public CatalogDatabasePreparer clearAuditLog() {
        preparers.add(() -> dsl.deleteFrom(CATALOG_AUDIT_LOG).execute());
        return this;
    }

    public CatalogDatabasePreparer clearProducts() {
        preparers.add(() -> dsl.deleteFrom(PRODUCTS).execute());
        return this;
    }

    public CatalogDatabasePreparer createProduct(ProductsPojo product) {
        preparers.add(() -> dsl.insertInto(PRODUCTS).set(dsl.newRecord(PRODUCTS, product)).execute());
        return this;
    }

    public DSLContext dsl() {
        return dsl;
    }

    public void prepare() {
        preparers.forEach(Runnable::run);
        preparers.clear();
    }
}
