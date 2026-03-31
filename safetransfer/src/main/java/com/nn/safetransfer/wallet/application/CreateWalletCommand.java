package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import lombok.Builder;

@Builder
public record CreateWalletCommand(
        TenantId tenantId,
        CustomerId customerId,
        CurrencyCode currency
) {
}
