package ru.vikulinva.customer.core.customer.usecase.updateprofile;

import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

import java.util.UUID;

public record UpdateProfileUseCase(
        UUID customerId,
        String firstName,
        String lastName,
        String phone
) implements UseCaseCommand<CustomerView> {
}
