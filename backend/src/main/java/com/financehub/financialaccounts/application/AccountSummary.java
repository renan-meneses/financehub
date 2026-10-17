package com.financehub.financialaccounts.application;

import com.financehub.financialaccounts.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPQL projection: never serializes a persistence entity. */
public record AccountSummary(UUID id,String name,String institution,AccountType type,String currency,
                             AccountStatus status,long version,Instant createdAt,BigDecimal balance) {
    public AccountView view() {
        return new AccountView(id,name,institution,type,currency,status,version,createdAt,
            new Money(balance,java.util.Currency.getInstance(currency)).amount().toPlainString());
    }
}
