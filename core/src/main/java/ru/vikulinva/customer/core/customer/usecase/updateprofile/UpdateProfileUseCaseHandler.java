package ru.vikulinva.customer.core.customer.usecase.updateprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.exception.CustomerNotFoundException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Name;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.Profile;
import ru.vikulinva.customer.core.customer.port.out.CustomerRepository;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.core.customer.usecase.mapper.CustomerViewAssembler;
import ru.vikulinva.usecase.UseCaseHandler;

import java.time.Clock;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional
public class UpdateProfileUseCaseHandler implements UseCaseHandler<UpdateProfileUseCase, CustomerView> {

    private final CustomerRepository customerRepository;
    private final Clock clock;
    private final CustomerViewAssembler viewAssembler;

    @Override
    public Class<UpdateProfileUseCase> useCaseType() {
        return UpdateProfileUseCase.class;
    }

    @Override
    public CustomerView handle(UpdateProfileUseCase useCase) {
        CustomerId id = CustomerId.of(useCase.customerId());
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        Profile profile = new Profile(
                new Name(useCase.firstName(), useCase.lastName()),
                Optional.ofNullable(useCase.phone()).map(Phone::of)
        );
        customer.updateProfile(profile, clock);
        customerRepository.save(customer);
        return viewAssembler.toView(customer);
    }
}
