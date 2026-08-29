package ru.vikulinva.customer.core.customer.port.out;

import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;

public interface VerificationTokenGenerator {

    VerificationTokenValue generate();
}
