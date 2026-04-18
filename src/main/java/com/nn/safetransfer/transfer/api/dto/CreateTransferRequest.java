package com.nn.safetransfer.transfer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Request for creating an idempotent wallet-to-wallet transfer.")
public record CreateTransferRequest(
        @Schema(description = "Wallet that will be debited.", example = "22222222-2222-2222-2222-222222222222")
        @NotNull UUID sourceWalletId,

        @Schema(description = "Wallet that will be credited.", example = "33333333-3333-3333-3333-333333333333")
        @NotNull UUID destinationWalletId,

        @Schema(description = "Transfer amount. Must be positive and use at most two decimal places.", example = "100.00")
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @Schema(description = "ISO 4217 currency code. Must match both wallets.", example = "EUR")
        @NotBlank String currency,

        @Schema(description = "Optional business reference for the transfer.", example = "Invoice INV-2026-0001")
        String reference
) {
}
