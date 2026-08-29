package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public sealed interface Discount extends ValueObject
    permits Discount.Percentage, Discount.Fixed {

    /**
     * Apply discount to the given amount, never producing a negative result.
     * If the computed discount exceeds the amount — capped at the amount (BR-012).
     */
    Money applyTo(Money amount);

    record Percentage(BigDecimal percent) implements Discount {

        public Percentage {
            Objects.requireNonNull(percent, "Discount.Percentage.percent must not be null");
            if (percent.signum() < 0) {
                throw new IllegalArgumentException("Percent must not be negative: " + percent);
            }
            if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percent must be <= 100: " + percent);
            }
        }

        @Override
        public Money applyTo(Money amount) {
            BigDecimal discounted = amount.amount()
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal capped = discounted.min(amount.amount());
            return amount.subtract(new Money(capped, amount.currency()));
        }
    }

    record Fixed(Money amount) implements Discount {

        public Fixed {
            Objects.requireNonNull(amount, "Discount.Fixed.amount must not be null");
        }

        @Override
        public Money applyTo(Money base) {
            BigDecimal capped = amount.amount().min(base.amount());
            return base.subtract(new Money(capped, base.currency()));
        }
    }
}
