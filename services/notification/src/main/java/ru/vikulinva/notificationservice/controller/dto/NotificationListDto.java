package ru.vikulinva.notificationservice.controller.dto;

import java.util.List;

public record NotificationListDto(
    List<NotificationSummaryDto> items,
    long total,
    int page,
    int size,
    boolean hasNext
) {
}
