package ru.vikulinva.notificationservice.controller.dto;

import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.generated.tables.pojos.DeliveryAttemptsPojo;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;

import java.util.List;

/**
 * Маппинг сгенерённых jOOQ-POJO → REST-DTO.
 */
@Component
public class RestMapper {

    public NotificationSummaryDto toSummary(NotificationsPojo n) {
        return new NotificationSummaryDto(
            n.getId(),
            n.getUserId(),
            n.getEventType(),
            n.getChannel(),
            n.getContact(),
            n.getStatus(),
            n.getCreatedAt(),
            n.getSentAt(),
            n.getDeliveredAt());
    }

    public NotificationDetailDto toDetail(NotificationsPojo n, List<DeliveryAttemptsPojo> attempts) {
        var attemptDtos = attempts.stream()
            .map(a -> new NotificationDetailDto.DeliveryAttemptDto(
                a.getId(),
                a.getAttemptNumber(),
                a.getResult().name(),
                a.getResponseSnippet(),
                a.getAttemptedAt()))
            .toList();
        return new NotificationDetailDto(
            toSummary(n),
            n.getTemplateKey(),
            n.getLocale(),
            n.getExternalId(),
            n.getLastError(),
            jsonb(n.getSourceEventPayload()),
            jsonb(n.getTemplateVariables()),
            attemptDtos);
    }

    private static String jsonb(JSONB value) {
        return value == null ? null : value.data();
    }
}
