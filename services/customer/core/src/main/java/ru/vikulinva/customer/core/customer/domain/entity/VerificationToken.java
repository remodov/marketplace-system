package ru.vikulinva.customer.core.customer.domain.entity;

import ru.vikulinva.customer.core.customer.domain.exception.TokenInvalidOrExpiredException;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.ddd.Entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class VerificationToken extends Entity<VerificationTokenValue> {

    public static final Duration TTL = Duration.ofHours(24);

    private final VerificationTokenValue id;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private VerificationToken(VerificationTokenValue id, Instant issuedAt, Instant expiresAt, Instant usedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.usedAt = usedAt;
    }

    @Override
    public VerificationTokenValue getId() {
        return id;
    }

    public static VerificationToken issue(VerificationTokenValue value, Instant now) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(now, "now");
        return new VerificationToken(value, now, now.plus(TTL), null);
    }

    public static VerificationToken rehydrate(VerificationTokenValue value,
                                              Instant issuedAt,
                                              Instant expiresAt,
                                              Instant usedAt) {
        return new VerificationToken(value, issuedAt, expiresAt, usedAt);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        if (usedAt != null) {
            throw new TokenInvalidOrExpiredException(getId());
        }
        this.usedAt = now;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
