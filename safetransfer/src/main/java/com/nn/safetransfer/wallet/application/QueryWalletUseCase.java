package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryWalletUseCase {
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public Wallet handle(GetWalletQuery query) {
        log.info("Querying wallet: walletId={}, tenantId={}", query.walletId(), query.tenantId());

        return walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .orElseThrow(() -> {
                    log.warn("Wallet not found: walletId={}, tenantId={}", query.walletId(), query.tenantId());
                    return new WalletNotFoundException(query.walletId(), query.tenantId());
                });
    }
}
