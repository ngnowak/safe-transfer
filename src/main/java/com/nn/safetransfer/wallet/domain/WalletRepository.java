package com.nn.safetransfer.wallet.domain;

import java.util.Optional;

public interface WalletRepository {
    Wallet save(Wallet wallet);

    Optional<Wallet> findByIdAndTenantId(WalletId walletId, TenantId tenantId);

    Optional<Wallet> findByIdAndTenantIdForUpdate(WalletId walletId, TenantId tenantId);
}
