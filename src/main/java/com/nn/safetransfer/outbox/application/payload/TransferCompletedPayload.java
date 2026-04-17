package com.nn.safetransfer.outbox.application.payload;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransferCompletedPayload(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency,
        String reference,
        String idempotencyKey
) {
}
