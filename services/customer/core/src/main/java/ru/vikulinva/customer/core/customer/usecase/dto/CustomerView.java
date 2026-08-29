package ru.vikulinva.customer.core.customer.usecase.dto;

import ru.vikulinva.customer.core.customer.domain.valueobject.Status;

import java.time.Instant;
import java.util.UUID;

public record CustomerView(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {
}
