package ru.vikulinva.customer.core.customer.usecase.getcustomer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.exception.CustomerNotFoundException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.port.out.CustomerRepository;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.core.customer.usecase.mapper.CustomerViewAssembler;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCustomerUseCaseHandler implements UseCaseHandler<GetCustomerUseCase, CustomerView> {

    private final CustomerRepository customerRepository;
    private final CustomerViewAssembler viewAssembler;

    @Override
    public Class<GetCustomerUseCase> useCaseType() {
        return GetCustomerUseCase.class;
    }

    @Override
    public CustomerView handle(GetCustomerUseCase useCase) {
        CustomerId id = CustomerId.of(useCase.customerId());
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return viewAssembler.toView(customer);
    }
}
