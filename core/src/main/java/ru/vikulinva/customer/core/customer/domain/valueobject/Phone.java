package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;
import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

public record Phone(String value) implements ValueObject {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    public Phone {
        Objects.requireNonNull(value, "Phone.value");
        if (!E164.matcher(value).matches()) {
            throw new ValidationException("phone must match E.164 format");
        }
    }

    public static Phone of(String raw) {
        return new Phone(raw);
    }
}
