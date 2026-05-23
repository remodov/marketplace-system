package ru.vikulinva.customer.core.customer.usecase.registercustomer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.exception.EmailAlreadyRegisteredException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.Name;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.Profile;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.core.customer.port.out.CustomerIdGenerator;
import ru.vikulinva.customer.core.customer.port.out.CustomerRepository;
import ru.vikulinva.customer.core.customer.port.out.VerificationTokenGenerator;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.core.customer.usecase.mapper.CustomerViewAssembler;
import ru.vikulinva.usecase.UseCaseHandler;

import java.time.Clock;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional
public class RegisterCustomerUseCaseHandler implements UseCaseHandler<RegisterCustomerUseCase, CustomerView> {

    private final CustomerRepository customerRepository;
    private final CustomerIdGenerator customerIdGenerator;
    private final VerificationTokenGenerator verificationTokenGenerator;
    private final Clock clock;
    private final CustomerViewAssembler viewAssembler;

    @Override
    public Class<RegisterCustomerUseCase> useCaseType() {
        return RegisterCustomerUseCase.class;
    }

    @Override
    public CustomerView handle(RegisterCustomerUseCase useCase) {
        Email email = Email.of(useCase.email());
        if (customerRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        CustomerId id = customerIdGenerator.generate();
        Profile profile = new Profile(
                new Name(useCase.firstName(), useCase.lastName()),
                Optional.ofNullable(useCase.phone()).map(Phone::of)
        );
        VerificationTokenValue token = verificationTokenGenerator.generate();
        Customer customer = Customer.register(id, email, profile, token, clock);
        customerRepository.save(customer);
        return viewAssembler.toView(customer);
    }
}
