package ru.vikulinva.customer.core.customer.usecase.mapper;

import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;

@Component
public class CustomerViewAssembler {

    public CustomerView toView(Customer customer) {
        return new CustomerView(
                customer.getId().value(),
                customer.getEmail().value(),
                customer.getProfile().firstName(),
                customer.getProfile().lastName(),
                customer.getProfile().phone().map(Phone::value).orElse(null),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
