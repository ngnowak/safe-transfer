package com.nn.safetransfer.transfer.domain;

import com.nn.safetransfer.wallet.domain.TenantId;

import java.util.Optional;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findByIdAndTenantId(TransferId transferId, TenantId tenantId);

    Optional<Transfer> findByTenantIdAndIdempotencyKey(TenantId tenantId, String idempotencyKey);

}
