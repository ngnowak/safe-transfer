package com.nn.safetransfer.transfer.infrastructure.mapper;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.transfer.infrastructure.persistence.TransferJpa;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    public TransferJpa toEntity(Transfer transfer) {
        return TransferJpa.builder()
                .id(transfer.getId().value())
                .tenantId(transfer.getTenantId().value())
                .sourceWalletId(transfer.getSourceWalletId().value())
                .destinationWalletId(transfer.getDestinationWalletId().value())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency().name())
                .status(transfer.getStatus().name())
                .idempotencyKey(transfer.getIdempotencyKey())
                .reference(transfer.getReference())
                .createdAt(transfer.getCreatedAt())
                .build();
    }

    public Transfer toDomain(TransferJpa jpa) {
        return toDomain(jpa, false);
    }

    public Transfer toDomain(TransferJpa jpa, boolean newlyCreated) {
        return Transfer.builder()
                .id(new TransferId(jpa.getId()))
                .tenantId(new TenantId(jpa.getTenantId()))
                .sourceWalletId(new WalletId(jpa.getSourceWalletId()))
                .destinationWalletId(new WalletId(jpa.getDestinationWalletId()))
                .money(Money.of(jpa.getAmount(), CurrencyCode.valueOf(jpa.getCurrency())))
                .status(TransferStatus.valueOf(jpa.getStatus()))
                .idempotencyKey(jpa.getIdempotencyKey())
                .reference(jpa.getReference())
                .createdAt(jpa.getCreatedAt())
                .newlyCreated(newlyCreated)
                .build();
    }
}
