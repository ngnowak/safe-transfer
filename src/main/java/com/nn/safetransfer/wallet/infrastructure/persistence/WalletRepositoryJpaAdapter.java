package com.nn.safetransfer.wallet.infrastructure.persistence;

import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import com.nn.safetransfer.wallet.infrastructure.persistence.mapper.WalletJpaMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class WalletRepositoryJpaAdapter implements WalletRepository {
    private final SpringDataWalletRepository walletRepository;
    private final WalletJpaMapper walletJpaMapper;

    @Override
    @Transactional
    public Wallet save(Wallet wallet) {
        var walletJpa = walletJpaMapper.toEntity(wallet);
        var savedWalletJpa = walletRepository.saveAndFlush(walletJpa);
        return walletJpaMapper.toDomain(savedWalletJpa);
    }

    @Override
    public Optional<Wallet> findByIdAndTenantId(WalletId walletId, TenantId tenantId) {
        return walletRepository.findByIdAndTenantId(walletId.value(), tenantId.value())
                .map(walletJpaMapper::toDomain);
    }

    @Override
    public Optional<Wallet> findByIdAndTenantIdForUpdate(WalletId walletId, TenantId tenantId) {
        return walletRepository
                .findByIdAndTenantIdForUpdate(walletId.value(), tenantId.value())
                .map(walletJpaMapper::toDomain);
    }
}
