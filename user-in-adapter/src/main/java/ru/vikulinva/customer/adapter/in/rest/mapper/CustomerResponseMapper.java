package ru.vikulinva.customer.adapter.in.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.generated.api.model.CustomerResponse;
import ru.vikulinva.customer.generated.api.model.CustomerStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface CustomerResponseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    CustomerResponse toRest(CustomerView view);

    default CustomerStatus mapStatus(ru.vikulinva.customer.core.customer.domain.valueobject.Status status) {
        return status == null ? null : CustomerStatus.valueOf(status.name());
    }

    default OffsetDateTime mapInstant(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
