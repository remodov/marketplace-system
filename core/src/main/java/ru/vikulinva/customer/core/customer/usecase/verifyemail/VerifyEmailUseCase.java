package ru.vikulinva.customer.core.customer.usecase.verifyemail;

import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.usecase.cqrs.UseCaseCommand;

public record VerifyEmailUseCase(String token) implements UseCaseCommand<CustomerView> {
}
