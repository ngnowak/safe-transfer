package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import org.springframework.stereotype.Component;

@Component
public class DepositResponseMapper {

    public DepositResponse toDepositResponse(LedgerEntry ledgerEntry) {
        return DepositResponse.builder()
                .ledgerEntryId(ledgerEntry.getId().toString())
                .walletId(ledgerEntry.getWalletId().toString())
                .amount(ledgerEntry.getAmount())
                .currency(ledgerEntry.getCurrency().name())
                .entryType(ledgerEntry.getType().name())
                .reference(ledgerEntry.getReference())
                .createdAt(ledgerEntry.getCreatedAt())
                .build();
    }
}
