package ru.vikulinva.customer.core.customer.domain.aggregate;

import ru.vikulinva.customer.core.customer.domain.entity.VerificationToken;
import ru.vikulinva.customer.core.customer.domain.event.CustomerEmailVerified;
import ru.vikulinva.customer.core.customer.domain.event.CustomerProfileUpdated;
import ru.vikulinva.customer.core.customer.domain.event.CustomerRegistered;
import ru.vikulinva.customer.core.customer.domain.exception.InvalidStatusTransitionException;
import ru.vikulinva.customer.core.customer.domain.exception.ProfileUpdateForbiddenStatusException;
import ru.vikulinva.customer.core.customer.domain.exception.TokenInvalidOrExpiredException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.Profile;
import ru.vikulinva.customer.core.customer.domain.valueobject.Status;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.ddd.AggregateRoot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Customer extends AggregateRoot<CustomerId> {

    private final CustomerId id;
    private final Email email;
    private Profile profile;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final List<VerificationToken> tokens;

    private Customer(CustomerId id,
                     Email email,
                     Profile profile,
                     Status status,
                     Instant createdAt,
                     Instant updatedAt,
                     long version,
                     List<VerificationToken> tokens) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.tokens = new ArrayList<>(Objects.requireNonNull(tokens, "tokens"));
    }

    @Override
    public CustomerId getId() {
        return id;
    }

    public static Customer register(CustomerId id,
                                    Email email,
                                    Profile profile,
                                    VerificationTokenValue tokenValue,
                                    Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        Customer customer = new Customer(
                id,
                email,
                profile,
                Status.PENDING_VERIFICATION,
                now,
                now,
                0L,
                new ArrayList<>()
        );
        VerificationToken token = VerificationToken.issue(tokenValue, now);
        customer.tokens.add(token);
        customer.registerEvent(new CustomerRegistered(
                id.value(),
                email.value(),
                profile.firstName(),
                profile.lastName(),
                profile.phone().map(Phone::value).orElse(null),
                tokenValue.value(),
                token.getExpiresAt(),
                now
        ));
        return customer;
    }

    public static Customer rehydrate(CustomerId id,
                                     Email email,
                                     Profile profile,
                                     Status status,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     long version,
                                     List<VerificationToken> tokens) {
        return new Customer(id, email, profile, status, createdAt, updatedAt, version, tokens);
    }

    public void verifyEmail(VerificationTokenValue tokenValue, Clock clock) {
        Objects.requireNonNull(tokenValue, "tokenValue");
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        VerificationToken token = findToken(tokenValue)
                .orElseThrow(() -> new TokenInvalidOrExpiredException(tokenValue));
        if (!token.isUsable(now)) {
            throw new TokenInvalidOrExpiredException(tokenValue);
        }
        if (status != Status.PENDING_VERIFICATION) {
            throw new InvalidStatusTransitionException(status, Status.ACTIVE);
        }
        token.markUsed(now);
        this.status = Status.ACTIVE;
        this.updatedAt = now;
        registerEvent(new CustomerEmailVerified(
                getId().value(),
                email.value(),
                now
        ));
    }

    public void updateProfile(Profile newProfile, Clock clock) {
        Objects.requireNonNull(newProfile, "newProfile");
        Objects.requireNonNull(clock, "clock");
        if (status != Status.ACTIVE) {
            throw new ProfileUpdateForbiddenStatusException(getId(), status);
        }
        Instant now = Instant.now(clock);
        this.profile = newProfile;
        this.updatedAt = now;
        registerEvent(new CustomerProfileUpdated(
                getId().value(),
                email.value(),
                newProfile.firstName(),
                newProfile.lastName(),
                newProfile.phone().map(Phone::value).orElse(null),
                now
        ));
    }

    public Email getEmail() {
        return email;
    }

    public Profile getProfile() {
        return profile;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<VerificationToken> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    private Optional<VerificationToken> findToken(VerificationTokenValue value) {
        return tokens.stream()
                .filter(t -> t.getId().equals(value))
                .findFirst();
    }
}
