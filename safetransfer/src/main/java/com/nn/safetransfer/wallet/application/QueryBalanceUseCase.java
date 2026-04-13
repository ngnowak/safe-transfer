package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryBalanceUseCase {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // TODO return result
    @Transactional(readOnly = true)
    public BalanceResult handle(GetBalanceQuery query) {
        var wallet = walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .orElseThrow(() -> new WalletNotFoundException(query.walletId(), query.tenantId()));

        var balance = ledgerEntryRepository.calculateBalance(query.tenantId(), query.walletId());

        return new BalanceResult(wallet, balance);
    }
}
