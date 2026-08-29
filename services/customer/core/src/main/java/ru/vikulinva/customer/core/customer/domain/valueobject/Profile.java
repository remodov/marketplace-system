package ru.vikulinva.customer.core.customer.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;
import java.util.Optional;

public record Profile(Name name, Optional<Phone> phone) implements ValueObject {

    public Profile {
        Objects.requireNonNull(name, "Profile.name");
        Objects.requireNonNull(phone, "Profile.phone");
    }

    public static Profile of(Name name, Phone phone) {
        return new Profile(name, Optional.ofNullable(phone));
    }

    public static Profile of(Name name) {
        return new Profile(name, Optional.empty());
    }

    public String firstName() {
        return name.firstName();
    }

    public String lastName() {
        return name.lastName();
    }
}
