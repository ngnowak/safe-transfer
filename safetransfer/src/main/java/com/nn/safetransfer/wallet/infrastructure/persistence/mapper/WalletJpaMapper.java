package com.nn.safetransfer.wallet.infrastructure.persistence.mapper;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletStatus;
import com.nn.safetransfer.wallet.infrastructure.persistence.WalletJpa;
import org.springframework.stereotype.Component;

@Component
public class WalletJpaMapper {

    public WalletJpa toEntity(Wallet wallet) {
        return WalletJpa.builder()
                .id(wallet.getId().value())
                .tenantId(wallet.getTenantId().value())
                .customerId(wallet.getCustomerId().value())
                .currency(wallet.getCurrency().name())
                .status(wallet.getStatus().name())
                .createdAt(wallet.getCreatedAt())
                .build();
    }

    public Wallet toDomain(WalletJpa entity) {
        return Wallet.builder()
                .id(new WalletId(entity.getId()))
                .tenantId(new TenantId(entity.getTenantId()))
                .customerId(new CustomerId(entity.getCustomerId()))
                .currency(CurrencyCode.valueOf(entity.getCurrency()))
                .status(WalletStatus.valueOf(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
