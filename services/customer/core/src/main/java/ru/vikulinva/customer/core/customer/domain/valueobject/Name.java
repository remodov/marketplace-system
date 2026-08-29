package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;
import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;

public record Name(String firstName, String lastName) implements ValueObject {

    private static final int MAX_PART_LENGTH = 100;

    public Name {
        Objects.requireNonNull(firstName, "Name.firstName");
        Objects.requireNonNull(lastName, "Name.lastName");
        String f = firstName.trim();
        String l = lastName.trim();
        if (f.isEmpty()) {
            throw new ValidationException("firstName must not be blank");
        }
        if (l.isEmpty()) {
            throw new ValidationException("lastName must not be blank");
        }
        if (f.length() > MAX_PART_LENGTH || l.length() > MAX_PART_LENGTH) {
            throw new ValidationException("name parts must be <= " + MAX_PART_LENGTH);
        }
        firstName = f;
        lastName = l;
    }

    public static Name of(String first, String last) {
        return new Name(first, last);
    }
}
