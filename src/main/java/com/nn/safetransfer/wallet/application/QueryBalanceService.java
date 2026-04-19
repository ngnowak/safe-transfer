package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryBalanceService implements QueryBalanceUseCase {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public Result<WalletError, BalanceResult> handle(GetBalanceQuery query) {
        log.debug("Querying balance: walletId={}, tenantId={}", query.walletId(), query.tenantId());

        return walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .<Result<WalletError, BalanceResult>>map(wallet -> {
                    var balance = ledgerEntryRepository.calculateBalance(query.tenantId(), query.walletId());
                    return Result.success(new BalanceResult(wallet, balance));
                })
                .orElseGet(() -> Result.failure(new WalletError.WalletNotFound(query.walletId(), query.tenantId())));
    }
}
