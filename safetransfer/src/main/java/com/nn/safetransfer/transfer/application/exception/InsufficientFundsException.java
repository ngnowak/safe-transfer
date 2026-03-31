package com.nn.safetransfer.transfer.application.exception;

import com.nn.safetransfer.wallet.domain.WalletId;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(WalletId walletId, BigDecimal available, BigDecimal requested) {
        super("Wallet '%s' has insufficient funds. Available: %s, requested: %s"
                .formatted(walletId.value(), available, requested));
    }
}
