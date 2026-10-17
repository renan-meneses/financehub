package com.financehub.creditcards.domain;

import com.financehub.financialaccounts.domain.Money;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Remainder minor units go to earliest installments, preserving the exact total. */
public final class InstallmentAllocator {
    private InstallmentAllocator() {}

    public static List<Money> allocate(Money total, int count) {
        total.requirePositive();
        if (count < 1 || count > 120) throw new IllegalArgumentException("Installment count must be 1..120");
        int scale = total.currency().getDefaultFractionDigits();
        BigInteger units = total.amount().movePointRight(scale).toBigIntegerExact();
        if (units.compareTo(BigInteger.valueOf(count)) < 0)
            throw new IllegalArgumentException("Every installment must contain at least one minor unit");
        BigInteger[] division = units.divideAndRemainder(BigInteger.valueOf(count));
        List<Money> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BigInteger value = division[0].add(i < division[1].intValueExact() ? BigInteger.ONE : BigInteger.ZERO);
            result.add(new Money(new BigDecimal(value, scale), total.currency()));
        }
        return List.copyOf(result);
    }
}
