package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) implements ValueObject {

    private static final int SCALE = 2;
    public static final Currency RUB = Currency.getInstance("RUB");

    public Money {
        Objects.requireNonNull(amount, "Money.amount must not be null");
        Objects.requireNonNull(currency, "Money.currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money.amount must not be negative: " + amount);
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money rub(String amount) {
        return new Money(new BigDecimal(amount), RUB);
    }

    public static Money rub(long amount) {
        return new Money(BigDecimal.valueOf(amount), RUB);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        ensureSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        ensureSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                "Money.subtract would produce negative amount: %s − %s".formatted(this, other));
        }
        return new Money(result, currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Money.multiply factor must not be negative: " + factor);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void ensureSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }
}
