package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryBalanceUseCase {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public Result<WalletError, BalanceResult> handle(GetBalanceQuery query) {
        return walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .<Result<WalletError, BalanceResult>>map(wallet -> {
                    var balance = ledgerEntryRepository.calculateBalance(query.tenantId(), query.walletId());
                    return Result.success(new BalanceResult(wallet, balance));
                })
                .orElseGet(() -> Result.failure(new WalletError.WalletNotFound(query.walletId(), query.tenantId())));
    }
}
