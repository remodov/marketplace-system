package ru.vikulinva.customer.core.customer.port.out;

import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.ddd.AggregateRepository;

import java.util.Optional;

public interface CustomerRepository extends AggregateRepository<Customer, CustomerId> {

    @Override
    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByEmail(Email email);

    Optional<Customer> findByVerificationToken(VerificationTokenValue token);

    boolean existsByEmail(Email email);

    @Override
    Customer save(Customer customer);

    @Override
    void delete(Customer customer);
}
