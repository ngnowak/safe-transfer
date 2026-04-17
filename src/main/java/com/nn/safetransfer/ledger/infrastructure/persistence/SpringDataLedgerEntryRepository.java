package com.nn.safetransfer.ledger.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpa, UUID> {

    @Query("""
            select coalesce(sum(
                case
                    when e.type = 'CREDIT' then e.amount
                    when e.type = 'DEBIT' then -e.amount
                    else 0
                end
            ), 0)
            from LedgerEntryJpa e
            where e.tenantId = :tenantId
              and e.walletId = :walletId
            """)
    BigDecimal calculateBalance(
            @Param("tenantId") UUID tenantId,
            @Param("walletId") UUID walletId
    );
}
