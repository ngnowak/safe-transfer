package com.nn.safetransfer.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Wallet balance derived from ledger entries.")
public record BalanceResponse(
        @Schema(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
        String walletId,
        @Schema(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
        String tenantId,
        @Schema(description = "ISO 4217 currency code.", example = "EUR")
        String currency,
        @Schema(description = "Current ledger-derived balance.", example = "900.00")
        BigDecimal balance
) {
}
