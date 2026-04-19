package com.nn.safetransfer.transfer.domain.event;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransferCompletedDomainEventTest {

    @Test
    void shouldCreateDomainEventFromTransfer() {
        var transfer = Transfer.completed(
                TenantId.create(),
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("42.00"),
                EUR,
                "idem-42",
                "test-hash",
                "reference"
        );

        var event = TransferCompletedDomainEvent.from(transfer);

        assertAll(
                () -> assertThat(event.tenantId()).isEqualTo(transfer.getTenantId()),
                () -> assertThat(event.transferId()).isEqualTo(transfer.getId()),
                () -> assertThat(event.sourceWalletId()).isEqualTo(transfer.getSourceWalletId()),
                () -> assertThat(event.destinationWalletId()).isEqualTo(transfer.getDestinationWalletId()),
                () -> assertThat(event.money()).isEqualTo(transfer.getMoney()),
                () -> assertThat(event.reference()).isEqualTo(transfer.getReference()),
                () -> assertThat(event.idempotencyKey()).isEqualTo(transfer.getIdempotencyKey()),
                () -> assertThat(event.occurredAt()).isNotNull()
        );
    }
}
