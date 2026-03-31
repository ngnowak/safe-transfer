package com.nn.safetransfer.transfer.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record TransferResponse(
        String transferId,
        String tenantId,
        String sourceWalletId,
        String destinationWalletId,
        BigDecimal amount,
        String currency,
        String status,
        String reference,
        Instant createdAt
) {
}
