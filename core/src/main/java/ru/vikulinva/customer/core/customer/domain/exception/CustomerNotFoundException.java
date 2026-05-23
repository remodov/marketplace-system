package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;

@Getter
public final class CustomerNotFoundException extends DomainException {

    private final CustomerId customerId;

    public CustomerNotFoundException(CustomerId customerId) {
        super("customer not found: " + customerId.value());
        this.customerId = customerId;
    }
}
