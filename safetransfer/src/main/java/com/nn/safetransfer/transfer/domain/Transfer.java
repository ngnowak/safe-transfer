package com.nn.safetransfer.transfer.domain;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import static com.nn.safetransfer.transfer.domain.TransferStatus.COMPLETED;

@Getter
public class Transfer {

    private final TransferId id;
    private final TenantId tenantId;
    private final WalletId sourceWalletId;
    private final WalletId destinationWalletId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final TransferStatus status;
    private final String idempotencyKey;
    private final String reference;
    private final Instant createdAt;

    @Builder
    public Transfer(
            TransferId id,
            TenantId tenantId,
            WalletId sourceWalletId,
            WalletId destinationWalletId,
            BigDecimal amount,
            CurrencyCode currency,
            TransferStatus status,
            String idempotencyKey,
            String reference,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sourceWalletId = Objects.requireNonNull(sourceWalletId, "sourceWalletId must not be null");
        this.destinationWalletId = Objects.requireNonNull(destinationWalletId, "destinationWalletId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        if (sourceWalletId.equals(destinationWalletId)) {
            throw new IllegalArgumentException("Source and destination wallets must be different");
        }

        this.reference = reference;
    }

    public static Transfer completed(
            TenantId tenantId,
            WalletId sourceWalletId,
            WalletId destinationWalletId,
            BigDecimal amount,
            CurrencyCode currency,
            String idempotencyKey,
            String reference
    ) {
        return Transfer.builder()
                .id(TransferId.newId())
                .tenantId(tenantId)
                .sourceWalletId(sourceWalletId)
                .destinationWalletId(destinationWalletId)
                .amount(amount)
                .currency(currency)
                .status(COMPLETED)
                .idempotencyKey(idempotencyKey)
                .reference(reference)
                .createdAt(Instant.now())
                .build();
    }
}