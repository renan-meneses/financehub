package com.financehub.financialaccounts.infrastructure;

import com.financehub.financialaccounts.domain.*;
import com.financehub.financialaccounts.application.AccountSummary;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount,UUID> {
    Optional<FinancialAccount> findByWorkspaceIdAndId(UUID workspaceId,UUID id);
    @Query(value="""
        SELECT new com.financehub.financialaccounts.application.AccountSummary(
            a.id,a.name,a.institution,a.type,a.currency,a.status,a.version,a.createdAt,COALESCE(SUM(e.amount),0))
        FROM FinancialAccount a LEFT JOIN AccountLedgerEntry e ON e.accountId=a.id AND e.workspaceId=a.workspaceId
        WHERE a.workspaceId=:workspace AND (:type IS NULL OR a.type=:type) AND (:status IS NULL OR a.status=:status)
        GROUP BY a.id,a.name,a.institution,a.type,a.currency,a.status,a.version,a.createdAt
        ORDER BY a.createdAt,a.id
        """,countQuery="""
        SELECT COUNT(a) FROM FinancialAccount a WHERE a.workspaceId=:workspace
        AND (:type IS NULL OR a.type=:type) AND (:status IS NULL OR a.status=:status)
        """)
    Page<AccountSummary> summaries(@Param("workspace") UUID workspace,@Param("type") AccountType type,
                                   @Param("status") AccountStatus status,Pageable pageable);
    @Query("""
        SELECT new com.financehub.financialaccounts.application.AccountSummary(
            a.id,a.name,a.institution,a.type,a.currency,a.status,a.version,a.createdAt,COALESCE(SUM(e.amount),0))
        FROM FinancialAccount a LEFT JOIN AccountLedgerEntry e ON e.accountId=a.id AND e.workspaceId=a.workspaceId
        WHERE a.workspaceId=:workspace AND a.id=:id
        GROUP BY a.id,a.name,a.institution,a.type,a.currency,a.status,a.version,a.createdAt
        """)
    Optional<AccountSummary> summary(@Param("workspace") UUID workspace,@Param("id") UUID id);
}
