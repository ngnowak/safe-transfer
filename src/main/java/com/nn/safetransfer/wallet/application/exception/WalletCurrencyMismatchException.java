package com.nn.safetransfer.wallet.application.exception;

import com.nn.safetransfer.wallet.domain.CurrencyCode;

public class WalletCurrencyMismatchException extends RuntimeException {

    public WalletCurrencyMismatchException(CurrencyCode expected, CurrencyCode actual) {
        super("Wallet currency is '%s' but request currency is '%s'".formatted(expected, actual));
    }
}