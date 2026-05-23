package ru.vikulinva.customer.bootstrap.service;

import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.core.customer.port.out.VerificationTokenGenerator;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class VerificationTokenGeneratorImpl implements VerificationTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public VerificationTokenValue generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new VerificationTokenValue(token);
    }
}
