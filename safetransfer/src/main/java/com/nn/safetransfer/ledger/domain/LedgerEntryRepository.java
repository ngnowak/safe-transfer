package com.nn.safetransfer.ledger.domain;

import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;

import java.math.BigDecimal;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry ledgerEntry);

    BigDecimal calculateBalance(TenantId tenantId, WalletId walletId);

}
