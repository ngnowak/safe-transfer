package com.nn.safetransfer.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Response returned after a successful deposit.")
public record DepositResponse(
        @Schema(description = "Ledger entry identifier created for the deposit.", example = "44444444-4444-4444-4444-444444444444")
        String ledgerEntryId,
        @Schema(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
        String walletId,
        @Schema(description = "Deposited amount.", example = "100.00")
        BigDecimal amount,
        @Schema(description = "ISO 4217 currency code.", example = "EUR")
        String currency,
        @Schema(description = "Ledger entry type.", example = "CREDIT")
        String entryType,
        @Schema(description = "Optional business reference.", example = "Initial account funding")
        String reference,
        @Schema(description = "Creation timestamp.", example = "2026-04-18T12:00:00Z")
        Instant createdAt
) {
}
