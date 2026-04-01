package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.Wallet;

import java.math.BigDecimal;

public record BalanceResult(
        Wallet wallet,
        BigDecimal balance
) {
}
