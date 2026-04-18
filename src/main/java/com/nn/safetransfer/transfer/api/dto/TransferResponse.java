package com.nn.safetransfer.transfer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Transfer details.")
public record TransferResponse(
        @Schema(description = "Transfer identifier.", example = "55555555-5555-5555-5555-555555555555")
        String transferId,
        @Schema(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
        String tenantId,
        @Schema(description = "Debited wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
        String sourceWalletId,
        @Schema(description = "Credited wallet identifier.", example = "33333333-3333-3333-3333-333333333333")
        String destinationWalletId,
        @Schema(description = "Transferred amount.", example = "100.00")
        BigDecimal amount,
        @Schema(description = "ISO 4217 currency code.", example = "EUR")
        String currency,
        @Schema(description = "Transfer status.", example = "COMPLETED")
        String status,
        @Schema(description = "Optional business reference.", example = "Invoice INV-2026-0001")
        String reference,
        @Schema(description = "Creation timestamp.", example = "2026-04-18T12:00:00Z")
        Instant createdAt
) {
}
