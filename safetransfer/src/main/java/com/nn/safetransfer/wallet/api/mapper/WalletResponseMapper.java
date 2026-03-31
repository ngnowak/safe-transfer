package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.domain.Wallet;
import org.springframework.stereotype.Component;

@Component
public class WalletResponseMapper {

    public WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId().toString())
                .tenantId(wallet.getTenantId().toString())
                .customerId(wallet.getCustomerId().toString())
                .currency(wallet.getCurrency().name())
                .status(wallet.getStatus().name())
                .build();
    }
}
