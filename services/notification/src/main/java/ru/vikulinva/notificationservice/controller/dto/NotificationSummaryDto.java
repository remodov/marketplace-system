package ru.vikulinva.notificationservice.controller.dto;

import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationSummaryDto(
    UUID id,
    UUID userId,
    String eventType,
    NotificationChannel channel,
    String contact,
    NotificationStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime sentAt,
    OffsetDateTime deliveredAt
) {
}
