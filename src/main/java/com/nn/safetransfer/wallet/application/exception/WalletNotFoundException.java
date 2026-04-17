package com.nn.safetransfer.wallet.application.exception;

import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(WalletId walletId, TenantId tenantId) {
        super("Wallet with id '%s' was not found for tenant '%s'"
                .formatted(walletId.value(), tenantId.value()));
    }

    public WalletNotFoundException(WalletId walletId) {
        super("Wallet with id '%s' was not found"
                .formatted(walletId.value()));
    }
}
