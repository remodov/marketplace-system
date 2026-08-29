package ru.vikulinva.customer.core.customer.usecase.registercustomer;

import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record RegisterCustomerUseCase(
        String email,
        String firstName,
        String lastName,
        String phone
) implements UseCaseCommand<CustomerView> {
}
