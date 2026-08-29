package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.Status;

@Getter
public final class InvalidStatusTransitionException extends DomainException {

    private final Status from;
    private final Status to;

    public InvalidStatusTransitionException(Status from, Status to) {
        super("invalid status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }
}
