package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryWalletService implements QueryWalletUseCase {

    private final WalletRepository walletRepository;

    @Override
    @Transactional(readOnly = true)
    public Result<WalletError, Wallet> handle(GetWalletQuery query) {
        log.debug("Querying wallet: walletId={}, tenantId={}", query.walletId(), query.tenantId());

        return walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .<Result<WalletError, Wallet>>map(Result::success)
                .orElseGet(() -> {
                    log.warn("Wallet not found: walletId={}, tenantId={}", query.walletId(), query.tenantId());
                    return Result.failure(new WalletError.WalletNotFound(query.walletId(), query.tenantId()));
                });
    }
}
