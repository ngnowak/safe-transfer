package com.nn.safetransfer.ledger.infrastructure.persistence;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.ledger.infrastructure.mapper.LedgerEntryMapper;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Repository
public class LedgerEntryRepositoryJpaAdapter implements LedgerEntryRepository {
    private final SpringDataLedgerEntryRepository springDataLedgerEntryRepository;
    private final LedgerEntryMapper ledgerEntryMapper;

    @Override
    public LedgerEntry save(LedgerEntry ledgerEntry) {
        var saved = springDataLedgerEntryRepository.save(
                ledgerEntryMapper.toEntity(ledgerEntry)
        );
        return ledgerEntryMapper.toDomain(saved);
    }

    @Override
    public BigDecimal calculateBalance(TenantId tenantId, WalletId walletId) {
        return springDataLedgerEntryRepository.calculateBalance(
                tenantId.value(),
                walletId.value()
        );
    }
}
