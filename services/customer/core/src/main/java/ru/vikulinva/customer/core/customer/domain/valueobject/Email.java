package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;
import ru.vikulinva.ddd.ValueObject;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) implements ValueObject {

    private static final int MAX_LENGTH = 254;
    private static final Pattern SYNTAX = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"
    );

    public Email {
        Objects.requireNonNull(value, "Email.value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ValidationException("email must not be blank");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new ValidationException("email length must be <= " + MAX_LENGTH);
        }
        if (!SYNTAX.matcher(normalized).matches()) {
            throw new ValidationException("email has invalid syntax");
        }
        value = normalized;
    }

    public static Email of(String raw) {
        return new Email(raw);
    }
}
