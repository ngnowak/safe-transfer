package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;

import java.math.BigDecimal;

public sealed interface WalletError {

    String getMessage();

    record DuplicateWallet(TenantId tenantId, CustomerId customerId, CurrencyCode currency) implements WalletError {
        @Override
        public String getMessage() {
            return "Wallet already exists for tenant %s, customer %s, and currency %s".formatted(tenantId, customerId, currency);
        }
    }

    record WalletNotFound(WalletId walletId, TenantId tenantId) implements WalletError {
        @Override
        public String getMessage() {
            return "Wallet with id '%s' was not found for tenant '%s'"
                    .formatted(walletId.value(), tenantId.value());
        }
    }

    record WalletNotActive(String detail) implements WalletError {
        @Override
        public String getMessage() {
            return detail;
        }
    }

    record CurrencyMismatch(CurrencyCode walletCurrency, CurrencyCode requestCurrency) implements WalletError {
        @Override
        public String getMessage() {
            return "Wallet currency is '%s' but request currency is '%s'"
                    .formatted(walletCurrency, requestCurrency);
        }
    }

    record InsufficientFunds(WalletId walletId, BigDecimal available, BigDecimal requested) implements WalletError {
        @Override
        public String getMessage() {
            return "Wallet '%s' has insufficient funds. Available: %s, requested: %s"
                    .formatted(walletId.value(), available, requested);
        }
    }

    record OtherError(String message) implements WalletError {
        public OtherError(final Throwable throwable) {
            this(throwable.getMessage());
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
