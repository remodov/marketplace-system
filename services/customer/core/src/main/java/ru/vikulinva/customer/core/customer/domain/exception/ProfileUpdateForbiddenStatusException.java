package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Status;

@Getter
public final class ProfileUpdateForbiddenStatusException extends DomainException {

    private final CustomerId customerId;
    private final Status currentStatus;

    public ProfileUpdateForbiddenStatusException(CustomerId customerId, Status currentStatus) {
        super("profile update forbidden in status " + currentStatus);
        this.customerId = customerId;
        this.currentStatus = currentStatus;
    }
}
