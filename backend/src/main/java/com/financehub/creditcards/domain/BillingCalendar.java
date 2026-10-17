package com.financehub.creditcards.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/** Calendar dates are interpreted in the workspace timezone before invoking this policy. */
public record BillingCalendar(int closingDay, int dueDay, boolean rollWeekendDueDates) {
    public BillingCalendar {
        if (closingDay < 1 || closingDay > 31 || dueDay < 1 || dueDay > 31)
            throw new IllegalArgumentException("Billing days must be 1..31");
    }

    public Cycle cycleFor(LocalDate purchaseDate) {
        YearMonth month = YearMonth.from(purchaseDate);
        if (purchaseDate.isAfter(clamp(month, closingDay))) month = month.plusMonths(1);
        return cycle(month);
    }

    public Cycle cycle(YearMonth closingMonth) {
        LocalDate closing = clamp(closingMonth, closingDay);
        LocalDate opening = clamp(closingMonth.minusMonths(1), closingDay).plusDays(1);
        LocalDate due = clamp(closingMonth, dueDay);
        if (!due.isAfter(closing)) due = clamp(closingMonth.plusMonths(1), dueDay);
        if (rollWeekendDueDates) {
            while (due.getDayOfWeek() == DayOfWeek.SATURDAY || due.getDayOfWeek() == DayOfWeek.SUNDAY)
                due = due.plusDays(1);
        }
        return new Cycle(opening, closing, due);
    }

    private static LocalDate clamp(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    public record Cycle(LocalDate openingDate, LocalDate closingDate, LocalDate dueDate) {}
}
