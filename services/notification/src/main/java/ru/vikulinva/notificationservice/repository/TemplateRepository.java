package ru.vikulinva.notificationservice.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.vikulinva.notificationservice.generated.tables.pojos.TemplatesPojo;

import java.util.Objects;
import java.util.Optional;

import static ru.vikulinva.notificationservice.generated.Tables.TEMPLATES;

@Repository
public class TemplateRepository {

    private final DSLContext dsl;

    public TemplateRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /**
     * Поиск с fallback на дефолтную локаль ({@code ru}).
     */
    public Optional<TemplatesPojo> find(String key, String locale) {
        return dsl.selectFrom(TEMPLATES)
            .where(TEMPLATES.KEY.eq(key))
            .and(TEMPLATES.LOCALE.in(locale, "ru"))
            .orderBy(TEMPLATES.LOCALE.eq(locale).desc())
            .limit(1)
            .fetchOptional()
            .map(r -> r.into(TemplatesPojo.class));
    }
}
