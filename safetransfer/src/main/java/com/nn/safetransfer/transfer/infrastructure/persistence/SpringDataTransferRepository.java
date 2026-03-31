package com.nn.safetransfer.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<TransferJpa, UUID> {

    Optional<TransferJpa> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
