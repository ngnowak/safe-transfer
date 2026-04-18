package com.nn.safetransfer.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Wallet metadata.")
public record WalletResponse(
        @Schema(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
        String walletId,
        @Schema(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
        String tenantId,
        @Schema(description = "Customer identifier.", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        String customerId,
        @Schema(description = "ISO 4217 currency code.", example = "EUR")
        String currency,
        @Schema(description = "Wallet lifecycle status.", example = "ACTIVE")
        String status
) {
}
