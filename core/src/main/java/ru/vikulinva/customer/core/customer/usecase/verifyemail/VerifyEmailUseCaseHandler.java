package ru.vikulinva.customer.core.customer.usecase.verifyemail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.exception.TokenInvalidOrExpiredException;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.core.customer.port.out.CustomerRepository;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.core.customer.usecase.mapper.CustomerViewAssembler;
import ru.vikulinva.usecase.UseCaseHandler;

import java.time.Clock;

@Component
@RequiredArgsConstructor
@Transactional
public class VerifyEmailUseCaseHandler implements UseCaseHandler<VerifyEmailUseCase, CustomerView> {

    private final CustomerRepository customerRepository;
    private final Clock clock;
    private final CustomerViewAssembler viewAssembler;

    @Override
    public Class<VerifyEmailUseCase> useCaseType() {
        return VerifyEmailUseCase.class;
    }

    @Override
    public CustomerView handle(VerifyEmailUseCase useCase) {
        VerificationTokenValue token = VerificationTokenValue.of(useCase.token());
        Customer customer = customerRepository.findByVerificationToken(token)
                .orElseThrow(() -> new TokenInvalidOrExpiredException(token));
        customer.verifyEmail(token, clock);
        customerRepository.save(customer);
        return viewAssembler.toView(customer);
    }
}
