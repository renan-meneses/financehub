package com.financehub.financialaccounts.application;
import com.financehub.financialaccounts.domain.*;
import java.time.Instant;
import java.util.UUID;
public record AccountView(UUID id,String name,String institution,AccountType type,String currency,
                          AccountStatus status,long version,Instant createdAt,String currentBalance) {}
