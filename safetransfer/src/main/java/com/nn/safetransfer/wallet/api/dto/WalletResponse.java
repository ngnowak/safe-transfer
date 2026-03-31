package com.nn.safetransfer.wallet.api.dto;

import lombok.Builder;

@Builder
public record WalletResponse(
        String walletId,
        String tenantId,
        String customerId,
        String currency,
        String status
) {
}
