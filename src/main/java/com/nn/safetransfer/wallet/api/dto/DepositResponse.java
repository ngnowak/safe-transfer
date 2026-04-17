package com.nn.safetransfer.wallet.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record DepositResponse(
        String ledgerEntryId,
        String walletId,
        BigDecimal amount,
        String currency,
        String entryType,
        String reference,
        Instant createdAt
) {
}
