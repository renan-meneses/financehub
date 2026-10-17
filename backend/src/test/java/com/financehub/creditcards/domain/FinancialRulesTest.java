package com.financehub.creditcards.domain;

import com.financehub.financialaccounts.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinancialRulesTest {
    private Money brl(String amount) { return new Money(new BigDecimal(amount), Currency.getInstance("BRL")); }

    @Test void allocatesRemainderToEarliestInstallments() {
        assertThat(InstallmentAllocator.allocate(brl("100.00"), 3))
            .containsExactly(brl("33.34"), brl("33.33"), brl("33.33"));
    }

    @Test void conservesPurchaseAmount() {
        for (int count = 1; count <= 120; count++) {
            var parts = InstallmentAllocator.allocate(brl("12345.67"), count);
            assertThat(parts.stream().reduce(brl("0"), Money::add)).isEqualTo(brl("12345.67"));
        }
    }

    @Test void clampsLeapYearClosingDateAndRollsWeekendDueDate() {
        var cycle = new BillingCalendar(31, 2, true).cycleFor(LocalDate.of(2024, 2, 29));
        assertThat(cycle.closingDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(cycle.dueDate()).isEqualTo(LocalDate.of(2024, 3, 4));
    }

    @Test void closingDayPurchaseRemainsInCurrentCycle() {
        var policy = new BillingCalendar(10, 20, false);
        assertThat(policy.cycleFor(LocalDate.of(2026, 1, 10)).closingDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(policy.cycleFor(LocalDate.of(2026, 1, 11)).closingDate()).isEqualTo(LocalDate.of(2026, 2, 10));
    }

    @Test void refusesImplicitRoundingAndCurrencyConversion() {
        assertThatThrownBy(() -> brl("0.001")).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> brl("1").add(new Money(BigDecimal.ONE, Currency.getInstance("USD"))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentAllocator.allocate(brl("0.01"), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
