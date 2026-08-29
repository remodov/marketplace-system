package ru.vikulinva.customer.core.customer.usecase.getcustomer;

import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.usecase.cqrs.UseCaseQuery;

import java.util.UUID;

public record GetCustomerUseCase(UUID customerId) implements UseCaseQuery<CustomerView> {
}
