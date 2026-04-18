package com.nn.safetransfer.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request for depositing funds into a wallet.")
public record DepositRequest(
        @Schema(description = "Deposit amount. Must be positive and use at most two decimal places.", example = "100.00")
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @Schema(description = "ISO 4217 currency code. Must match the wallet currency.", example = "EUR")
        @NotBlank
        String currency,

        @Schema(description = "Optional business reference shown in ledger history.", example = "Initial account funding")
        String reference
) {
}
