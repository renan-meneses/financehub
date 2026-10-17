import com.financehub.creditcards.domain.BillingCalendar;
import com.financehub.creditcards.domain.InstallmentAllocator;
import com.financehub.financialaccounts.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;

/** Dependency-free check for restricted environments; does not replace Maven integration tests. */
public class FinancialRulesCheck {
    private static int checks;
    private static Money money(String amount) { return new Money(new BigDecimal(amount), Currency.getInstance("BRL")); }
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Check " + checks + " failed");
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | ArithmeticException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) {
        check(InstallmentAllocator.allocate(money("100"), 3).getFirst().equals(money("33.34")));
        check(InstallmentAllocator.allocate(money("1200"), 6).stream().allMatch(money("200")::equals));
        for (int units = 120; units < 5000; units += 37) {
            Money total = money(BigDecimal.valueOf(units, 2).toPlainString());
            for (int count = 1; count <= 120; count++) {
                var parts = InstallmentAllocator.allocate(total, count);
                check(parts.size() == count);
                check(parts.stream().reduce(money("0"), Money::add).equals(total));
                check(parts.stream().allMatch(p -> p.amount().signum() > 0));
                check(parts.getFirst().amount().subtract(parts.getLast().amount()).compareTo(new BigDecimal("0.01")) <= 0);
            }
        }
        rejects(() -> money("1.001"));
        rejects(() -> InstallmentAllocator.allocate(money("0"), 1));
        rejects(() -> InstallmentAllocator.allocate(money("0.01"), 2));
        rejects(() -> InstallmentAllocator.allocate(money("100"), 121));
        rejects(() -> money("1").add(new Money(BigDecimal.ONE, Currency.getInstance("USD"))));
        var policy = new BillingCalendar(31, 2, true);
        check(policy.cycleFor(LocalDate.of(2024, 2, 29)).dueDate().equals(LocalDate.of(2024, 3, 4)));
        check(policy.cycleFor(LocalDate.of(2025, 2, 28)).closingDate().equals(LocalDate.of(2025, 2, 28)));
        for (int year = 2020; year <= 2030; year++) {
            for (int month = 1; month <= 12; month++) {
                for (int closing = 1; closing <= 31; closing++) {
                    var calendar = new BillingCalendar(closing, closing, true);
                    var cycle = calendar.cycle(YearMonth.of(year, month));
                    check(cycle.dueDate().isAfter(cycle.closingDate()));
                    check(cycle.openingDate().equals(calendar.cycle(YearMonth.of(year, month).minusMonths(1)).closingDate().plusDays(1)));
                    check(calendar.cycleFor(cycle.closingDate()).equals(cycle));
                    check(calendar.cycleFor(cycle.closingDate().plusDays(1)).closingDate().isAfter(cycle.closingDate()));
                }
            }
        }
        System.out.println("Passed " + checks + " financial rule assertions");
    }
}
