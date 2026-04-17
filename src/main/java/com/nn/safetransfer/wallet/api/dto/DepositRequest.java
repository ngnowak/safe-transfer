package com.nn.safetransfer.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @NotBlank
        String currency,

        String reference
) {
}
