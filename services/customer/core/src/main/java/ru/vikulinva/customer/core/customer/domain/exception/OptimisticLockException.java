package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;

@Getter
public final class OptimisticLockException extends DomainException {

    private final CustomerId customerId;

    public OptimisticLockException(CustomerId customerId) {
        super("optimistic lock conflict for customer " + customerId.value());
        this.customerId = customerId;
    }
}
