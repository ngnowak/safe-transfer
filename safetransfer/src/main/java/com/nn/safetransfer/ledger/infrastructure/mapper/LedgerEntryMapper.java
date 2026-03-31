package com.nn.safetransfer.ledger.infrastructure.mapper;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryId;
import com.nn.safetransfer.ledger.domain.LedgerEntryType;
import com.nn.safetransfer.ledger.infrastructure.persistence.LedgerEntryJpa;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.springframework.stereotype.Component;

@Component
public class LedgerEntryMapper {

    public LedgerEntryJpa toEntity(LedgerEntry ledgerEntry) {
        return LedgerEntryJpa.builder()
                .id(ledgerEntry.getId().value())
                .tenantId(ledgerEntry.getTenantId().value())
                .walletId(ledgerEntry.getWalletId().value())
                .type(ledgerEntry.getType().name())
                .amount(ledgerEntry.getAmount())
                .currency(ledgerEntry.getCurrency().name())
                .reference(ledgerEntry.getReference())
                .createdAt(ledgerEntry.getCreatedAt())
                .build();
    }

    public LedgerEntry toDomain(LedgerEntryJpa jpa) {
        return LedgerEntry.builder()
                .id(new LedgerEntryId(jpa.getId()))
                .tenantId(new TenantId(jpa.getTenantId()))
                .walletId(new WalletId(jpa.getWalletId()))
                .type(LedgerEntryType.valueOf(jpa.getType()))
                .amount(jpa.getAmount())
                .currency(CurrencyCode.valueOf(jpa.getCurrency()))
                .reference(jpa.getReference())
                .createdAt(jpa.getCreatedAt())
                .build();
    }
}
