package ru.vikulinva.notificationservice.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationDetailDto(
    NotificationSummaryDto summary,
    String templateKey,
    String locale,
    String externalId,
    String lastError,
    String sourceEventPayload,
    String templateVariables,
    List<DeliveryAttemptDto> attempts
) {

    public record DeliveryAttemptDto(
        UUID id,
        int attemptNumber,
        String result,
        String responseSnippet,
        OffsetDateTime attemptedAt
    ) {}
}
