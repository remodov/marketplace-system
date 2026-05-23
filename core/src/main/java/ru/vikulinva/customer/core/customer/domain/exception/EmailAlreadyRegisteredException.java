package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;

@Getter
public final class EmailAlreadyRegisteredException extends DomainException {

    private final Email email;

    public EmailAlreadyRegisteredException(Email email) {
        super("email already registered: " + email.value());
        this.email = email;
    }
}
