package ru.remodov.catalog.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.core.service.DateTimeService;
import ru.remodov.catalog.core.service.UuidGenerator;
import ru.remodov.catalog.domain.SellerId;
import ru.remodov.catalog.generated.tables.pojos.CatalogAuditLogPojo;
import ru.remodov.catalog.repository.AuditLogRepository;

@Component
@RequiredArgsConstructor
public class AuditLogger {

    public static final String ACTION_PRODUCT_PUBLISHED = "PRODUCT_PUBLISHED";
    public static final String ACTION_PRODUCT_HIDDEN = "PRODUCT_HIDDEN";

    private final AuditLogRepository repo;
    private final UuidGenerator uuidGenerator;
    private final DateTimeService dateTimeService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAdminAction(SellerId actor, String action, UUID productId, Map<String, ?> metadata) {
        var entry = new CatalogAuditLogPojo();
        entry.setId(uuidGenerator.generate());
        entry.setActorId(actor.value());
        entry.setAction(action);
        entry.setProductId(productId);
        entry.setOccurredAt(dateTimeService.now().atOffset(ZoneOffset.UTC));
        entry.setMetadata(JSONB.valueOf(serialize(metadata)));
        repo.insert(entry);
    }

    private String serialize(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
