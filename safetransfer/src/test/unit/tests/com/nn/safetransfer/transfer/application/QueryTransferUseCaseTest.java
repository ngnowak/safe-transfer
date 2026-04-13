package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.transfer.domain.TransferRepository;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueryTransferUseCaseTest {

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private QueryTransferUseCase queryTransferUseCase;

    @Test
    void shouldReturnTransferWhenFound() {
        var tenantId = TenantId.create();
        var transfer = Transfer.completed(
                tenantId,
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("10.00"),
                EUR,
                "idem",
                "ref"
        );
        var query = GetTransferQuery.builder()
                .tenantId(tenantId)
                .transferId(transfer.getId())
                .build();

        given(transferRepository.findByIdAndTenantId(transfer.getId(), tenantId)).willReturn(Optional.of(transfer));

        var result = queryTransferUseCase.handle(query);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).contains(transfer);
    }

    @Test
    void shouldReturnFailureWhenTransferNotFound() {
        var tenantId = TenantId.create();
        var transferId = TransferId.newId();
        var query = GetTransferQuery.builder()
                .tenantId(tenantId)
                .transferId(transferId)
                .build();

        given(transferRepository.findByIdAndTenantId(transferId, tenantId)).willReturn(Optional.empty());

        var result = queryTransferUseCase.handle(query);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains(new TransferError.TransferNotFound(transferId, tenantId));
    }
}
