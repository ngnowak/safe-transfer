package com.nn.safetransfer.wallet.application;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DepositCommand(
        BigDecimal amount,
        String currency,
        String reference
) {
}
