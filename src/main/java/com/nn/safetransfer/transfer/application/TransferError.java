package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;

import java.math.BigDecimal;

public sealed interface TransferError {

    String getMessage();

    record SameWalletTransfer() implements TransferError {
        @Override
        public String getMessage() {
            return "Source and destination wallets must be different";
        }
    }

    record WalletNotFound(WalletId walletId, TenantId tenantId) implements TransferError {
        @Override
        public String getMessage() {
            return "Wallet with id '%s' was not found for tenant '%s'"
                    .formatted(walletId.value(), tenantId.value());
        }
    }

    record TransferNotFound(com.nn.safetransfer.transfer.domain.TransferId transferId, TenantId tenantId) implements TransferError {
        @Override
        public String getMessage() {
            return "Transfer with id '%s' was not found for tenant '%s'"
                    .formatted(transferId.value(), tenantId.value());
        }
    }

    record WalletNotActive(String detail) implements TransferError {
        @Override
        public String getMessage() {
            return detail;
        }
    }

    record CurrencyMismatch(CurrencyCode walletCurrency, CurrencyCode requestCurrency) implements TransferError {
        @Override
        public String getMessage() {
            return "Wallet currency is '%s' but request currency is '%s'"
                    .formatted(walletCurrency, requestCurrency);
        }
    }

    record InsufficientFunds(WalletId walletId, BigDecimal available, BigDecimal requested) implements TransferError {
        @Override
        public String getMessage() {
            return "Wallet '%s' has insufficient funds. Available: %s, requested: %s"
                    .formatted(walletId.value(), available, requested);
        }
    }

    record TransferLimitExceeded(BigDecimal requested, BigDecimal limit) implements TransferError {
        @Override
        public String getMessage() {
            return "Transfer amount %s exceeds configured single transfer limit %s"
                    .formatted(requested, limit);
        }
    }

    record IdempotencyKeyConflict(String idempotencyKey) implements TransferError {
        @Override
        public String getMessage() {
            return "Idempotency key '%s' was already used with a different transfer request"
                    .formatted(idempotencyKey);
        }
    }

    record OtherError(String message) implements TransferError {
        public OtherError(final Throwable throwable) {
            this(throwable.getMessage());
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
