package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryWalletUseCase {
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public Wallet handle(GetWalletQuery query) {
        return walletRepository.findByIdAndTenantId(query.walletId(), query.tenantId())
                .orElseThrow(() -> new WalletNotFoundException(query.walletId(), query.tenantId()));
    }
}
