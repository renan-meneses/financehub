package com.financehub.financialaccounts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Exact amounts in currencies with zero to four fractional digits. No implicit FX. */
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0 || digits > 4) throw new IllegalArgumentException("Unsupported currency precision");
        amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
        if (amount.precision() > 19) throw new IllegalArgumentException("Amount exceeds storage precision");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() { return new Money(amount.negate(), currency); }

    public Money requirePositive() {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        return this;
    }

    public void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
    }
}
