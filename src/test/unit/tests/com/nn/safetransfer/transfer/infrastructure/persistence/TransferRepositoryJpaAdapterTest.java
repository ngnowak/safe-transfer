package com.nn.safetransfer.transfer.infrastructure.persistence;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.transfer.infrastructure.mapper.TransferMapper;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferRepositoryJpaAdapterTest {

    @Mock
    private SpringDataTransferRepository springDataTransferRepository;

    @Mock
    private TransferMapper transferMapper;

    @InjectMocks
    private TransferRepositoryJpaAdapter adapter;

    @Test
    void shouldPreserveNewlyCreatedFlagWhenSaving() {
        var transfer = Transfer.completed(
                TenantId.create(),
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("10.00"),
                EUR,
                "idem",
                "test-hash",
                "ref"
        );
        var savedEntity = TransferJpa.builder().build();
        var mappedTransfer = Transfer.completed(
                transfer.getTenantId(),
                transfer.getSourceWalletId(),
                transfer.getDestinationWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getIdempotencyKey(),
                transfer.getRequestHash(),
                transfer.getReference()
        );

        given(transferMapper.toEntity(transfer)).willReturn(savedEntity);
        given(springDataTransferRepository.save(savedEntity)).willReturn(savedEntity);
        given(transferMapper.toDomain(savedEntity, true)).willReturn(mappedTransfer);

        var result = adapter.save(transfer);

        assertThat(result).isEqualTo(mappedTransfer);
        verify(transferMapper).toDomain(savedEntity, true);
    }

    @Test
    void shouldFindTransferByIdAndTenantId() {
        var tenantId = TenantId.create();
        var transferId = TransferId.newId();
        var entity = TransferJpa.builder().id(transferId.value()).tenantId(tenantId.value()).build();
        var transfer = Transfer.completed(
                tenantId,
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("10.00"),
                EUR,
                "idem",
                "test-hash",
                "ref"
        );

        given(springDataTransferRepository.findByIdAndTenantId(transferId.value(), tenantId.value())).willReturn(Optional.of(entity));
        given(transferMapper.toDomain(entity)).willReturn(transfer);

        var result = adapter.findByIdAndTenantId(transferId, tenantId);

        assertAll(
                () -> assertThat(result).contains(transfer),
                () -> verify(transferMapper).toDomain(entity)
        );
    }

    @Test
    void shouldReturnEmptyWhenTransferIsNotFoundByIdAndTenantId() {
        var tenantId = TenantId.create();
        var transferId = TransferId.newId();

        given(springDataTransferRepository.findByIdAndTenantId(transferId.value(), tenantId.value()))
                .willReturn(Optional.empty());

        var result = adapter.findByIdAndTenantId(transferId, tenantId);

        assertThat(result).isEmpty();
        verify(transferMapper, never()).toDomain(any(TransferJpa.class));
    }

    @Test
    void shouldFindTransferByTenantIdAndIdempotencyKey() {
        var tenantId = TenantId.create();
        var entity = TransferJpa.builder().tenantId(tenantId.value()).idempotencyKey("idem").build();
        var transfer = Transfer.completed(
                tenantId,
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("10.00"),
                EUR,
                "idem",
                "test-hash",
                "ref"
        );

        given(springDataTransferRepository.findByTenantIdAndIdempotencyKey(tenantId.value(), "idem"))
                .willReturn(Optional.of(entity));
        given(transferMapper.toDomain(entity)).willReturn(transfer);

        var result = adapter.findByTenantIdAndIdempotencyKey(tenantId, "idem");

        assertAll(
                () -> assertThat(result).contains(transfer),
                () -> verify(transferMapper).toDomain(entity)
        );
    }

    @Test
    void shouldReturnEmptyWhenTransferIsNotFoundByTenantIdAndIdempotencyKey() {
        var tenantId = TenantId.create();

        given(springDataTransferRepository.findByTenantIdAndIdempotencyKey(tenantId.value(), "missing"))
                .willReturn(Optional.empty());

        var result = adapter.findByTenantIdAndIdempotencyKey(tenantId, "missing");

        assertThat(result).isEmpty();
        verify(transferMapper, never()).toDomain(any(TransferJpa.class));
    }
}
