package ru.vikulinva.customer.bootstrap.service;

import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.port.out.CustomerIdGenerator;

import java.util.UUID;

@Component
public class RandomCustomerIdGenerator implements CustomerIdGenerator {

    @Override
    public CustomerId generate() {
        return CustomerId.of(UUID.randomUUID());
    }
}
