package com.nn.safetransfer.wallet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

@Repository
public interface SpringDataWalletRepository extends JpaRepository<WalletJpa, UUID> {

    boolean existsByTenantIdAndCustomerIdAndCurrency(UUID tenantId, UUID customerId, String currency);

    Optional<WalletJpa> findByIdAndTenantId(UUID walletId, UUID tenantId);

    @Lock(PESSIMISTIC_WRITE)
    @Query("""
            select w
            from WalletJpa w
            where w.id = :id
              and w.tenantId = :tenantId
            """)
    Optional<WalletJpa> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );
}
