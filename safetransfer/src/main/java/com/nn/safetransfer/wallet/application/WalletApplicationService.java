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
public class WalletApplicationService implements CreateWalletUseCase {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Result<WalletError, Wallet> handle(CreateWalletCommand command) {
        log.info("Creating wallet: tenantId={}, customerId={}, currency={}", command.tenantId(), command.customerId(), command.currency());

        var alreadyExists = walletRepository.existsByTenantIdAndCustomerIdAndCurrency(
                command.tenantId(),
                command.customerId(),
                command.currency()
        );

        if (alreadyExists) {
            log.warn("Duplicate wallet attempt: tenantId={}, customerId={}, currency={}", command.tenantId(), command.customerId(), command.currency());
            return Result.failure(new WalletError.DuplicateWallet(command.tenantId(), command.customerId(), command.currency()));
        }

        var wallet = Wallet.create(
                command.tenantId(),
                command.customerId(),
                command.currency()
        );

        var saved = walletRepository.save(wallet);
        log.info("Wallet created: walletId={}", saved.getId());

        return Result.success(saved);
    }
}