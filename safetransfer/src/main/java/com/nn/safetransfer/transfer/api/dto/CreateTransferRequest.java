package com.nn.safetransfer.transfer.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CreateTransferRequest(
        @NotBlank UUID sourceWalletId,
        @NotBlank UUID destinationWalletId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @NotBlank String currency,
        String reference
) {
}
