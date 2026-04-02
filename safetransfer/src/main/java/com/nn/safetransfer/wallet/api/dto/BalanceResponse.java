package com.nn.safetransfer.wallet.api.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BalanceResponse(
        String walletId,
        String tenantId,
        String currency,
        BigDecimal balance
) {
}
