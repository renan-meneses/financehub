package com.financehub.expenses.application;

import java.time.LocalDate;
import java.util.*;
import com.financehub.financialaccounts.domain.AccountStatus;

public final class FinanceViews {
    private FinanceViews() {}
    public enum DebtStatus { PENDING, PAID, CANCELLED }
    public record Category(UUID id,String name,AccountStatus status,long version) {}
    public record Debt(UUID id,UUID accountId,String accountName,UUID categoryId,String categoryName,
                       String description,String amount,String currency,LocalDate dueDate,DebtStatus status,LocalDate paidDate) {}
    public record Totals(String currency,String balance,String pending,String overdue,String paid,String previousPaid,
                         String availableAfterPending,boolean shortfall,String changePercent) {}
    public record CategoryTotal(UUID id,String name,String currency,String amount,String sharePercent) {}
    public record MonthTotal(String month,String currency,String amount) {}
    public record Analysis(LocalDate from,LocalDate to,LocalDate asOf,List<Totals> totals,
                           List<CategoryTotal> categories,List<MonthTotal> months) {}
    public record Created<T>(T value,boolean replayed) {}
}
