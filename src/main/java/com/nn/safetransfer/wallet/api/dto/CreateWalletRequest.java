package com.nn.safetransfer.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request for creating a tenant-scoped wallet.")
public record CreateWalletRequest(
        @Schema(description = "Customer that owns the wallet.", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        @NotNull UUID customerId,

        @Schema(description = "ISO 4217 currency code.", example = "EUR")
        @NotBlank String currency
) {
}
