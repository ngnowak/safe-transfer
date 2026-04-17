package com.nn.safetransfer.transfer.domain.event;

import com.nn.safetransfer.common.domain.event.DomainEvent;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;

import java.time.Instant;

public record TransferCompletedDomainEvent(
        TenantId tenantId,
        TransferId transferId,
        WalletId sourceWalletId,
        WalletId destinationWalletId,
        Money money,
        String reference,
        String idempotencyKey,
        Instant occurredAt
) implements DomainEvent {

    public static TransferCompletedDomainEvent from(Transfer transfer) {
        return new TransferCompletedDomainEvent(
                transfer.getTenantId(),
                transfer.getId(),
                transfer.getSourceWalletId(),
                transfer.getDestinationWalletId(),
                transfer.getMoney(),
                transfer.getReference(),
                transfer.getIdempotencyKey(),
                Instant.now()
        );
    }
}
