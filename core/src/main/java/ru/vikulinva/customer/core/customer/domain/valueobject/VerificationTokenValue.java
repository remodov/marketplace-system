package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;
import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

public record VerificationTokenValue(String value) implements ValueObject {

    private static final int MIN_LENGTH = 22;
    private static final Pattern URL_SAFE = Pattern.compile("^[A-Za-z0-9_\\-]+$");

    public VerificationTokenValue {
        Objects.requireNonNull(value, "VerificationTokenValue.value");
        if (value.length() < MIN_LENGTH) {
            throw new ValidationException("verification token must be at least " + MIN_LENGTH + " chars");
        }
        if (!URL_SAFE.matcher(value).matches()) {
            throw new ValidationException("verification token must be URL-safe");
        }
    }

    public static VerificationTokenValue of(String raw) {
        return new VerificationTokenValue(raw);
    }
}
