package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class WalletApplicationService implements CreateWalletUseCase {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Wallet handle(CreateWalletCommand command) {
        var alreadyExists = walletRepository.existsByTenantIdAndCustomerIdAndCurrency(
                command.tenantId(),
                command.customerId(),
                command.currency()
        );

        if (alreadyExists) {
            throw new IllegalArgumentException("Wallet already exists for this tenant, customer, and currency");
        }

        var wallet = Wallet.create(
                command.tenantId(),
                command.customerId(),
                command.currency()
        );

        return walletRepository.save(wallet);
    }
}