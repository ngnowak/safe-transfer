package com.nn.safetransfer.transfer.infrastructure.persistence;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
import com.nn.safetransfer.transfer.infrastructure.mapper.TransferMapper;
import com.nn.safetransfer.wallet.domain.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class TransferRepositoryJpaAdapter implements TransferRepository {

    private final SpringDataTransferRepository springDataTransferRepository;
    private final TransferMapper transferMapper;

    @Override
    public Transfer save(Transfer transfer) {
        var saved = springDataTransferRepository.save(transferMapper.toEntity(transfer));
        return transferMapper.toDomain(saved);
    }

    @Override
    public Optional<Transfer> findByTenantIdAndIdempotencyKey(TenantId tenantId, String idempotencyKey) {
        return springDataTransferRepository
                .findByTenantIdAndIdempotencyKey(tenantId.value(), idempotencyKey)
                .map(transferMapper::toDomain);
    }
}
