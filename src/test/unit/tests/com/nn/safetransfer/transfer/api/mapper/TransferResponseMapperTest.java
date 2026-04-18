package com.nn.safetransfer.transfer.api.mapper;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransferResponseMapperTest {

    private final TransferResponseMapper mapper = new TransferResponseMapper();

    @Test
    void shouldMapTransferToTransferResponse() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var amount = new BigDecimal("75.25");
        var transfer = Transfer.completed(
                tenantId, sourceWalletId, destinationWalletId,
                amount, USD, "idem-key", "Ref-123"
        );

        // when
        var response = mapper.toResponse(transfer);

        // then
        assertAll(
                () -> assertThat(response.transferId()).isEqualTo(transfer.getId().value().toString()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.value().toString()),
                () -> assertThat(response.sourceWalletId()).isEqualTo(sourceWalletId.value().toString()),
                () -> assertThat(response.destinationWalletId()).isEqualTo(destinationWalletId.value().toString()),
                () -> assertThat(response.amount()).isEqualByComparingTo(amount),
                () -> assertThat(response.currency()).isEqualTo(USD.name()),
                () -> assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED.name()),
                () -> assertThat(response.reference()).isEqualTo(transfer.getReference()),
                () -> assertThat(response.createdAt()).isNotNull()
        );
    }

    @Test
    void shouldMapTransferWithNullReference() {
        // given
        var transfer = Transfer.completed(
                TenantId.create(), WalletId.create(), WalletId.create(),
                new BigDecimal("10.00"), USD, "key", null
        );

        // when
        var response = mapper.toResponse(transfer);

        // then
        assertThat(response.reference()).isNull();
    }
}
