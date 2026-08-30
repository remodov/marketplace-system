package ru.vikulinva.notificationservice.controller.dto;

import org.jooq.JSONB;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.vikulinva.notificationservice.generated.tables.pojos.DeliveryAttemptsPojo;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RestMapper {

    NotificationSummaryDto toSummary(NotificationsPojo n);

    @Mapping(target = "summary", source = "n")
    @Mapping(target = "attempts", source = "attempts")
    NotificationDetailDto toDetail(NotificationsPojo n, List<DeliveryAttemptsPojo> attempts);

    NotificationDetailDto.DeliveryAttemptDto toAttempt(DeliveryAttemptsPojo attempt);

    default String toJsonText(JSONB value) {
        return value == null ? null : value.data();
    }
}
