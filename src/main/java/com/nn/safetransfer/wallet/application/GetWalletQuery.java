package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import lombok.Builder;

@Builder
public record GetWalletQuery(
        TenantId tenantId,
        WalletId walletId
) {
}
