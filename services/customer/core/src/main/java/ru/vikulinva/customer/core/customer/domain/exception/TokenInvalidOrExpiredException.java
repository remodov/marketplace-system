package ru.vikulinva.customer.core.customer.domain.exception;

import lombok.Getter;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;

@Getter
public final class TokenInvalidOrExpiredException extends DomainException {

    private final VerificationTokenValue token;

    public TokenInvalidOrExpiredException(VerificationTokenValue token) {
        super("verification token invalid or expired");
        this.token = token;
    }
}
