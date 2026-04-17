package com.nn.safetransfer.transfer.domain;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.Money;
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
    private final Money money;
    private final TransferStatus status;
    private final String idempotencyKey;
    private final String requestHash;
    private final String reference;
    private final Instant createdAt;
    private final boolean newlyCreated;

    @Builder
    public Transfer(
            TransferId id,
            TenantId tenantId,
            WalletId sourceWalletId,
            WalletId destinationWalletId,
            Money money,
            TransferStatus status,
            String idempotencyKey,
            String requestHash,
            String reference,
            Instant createdAt,
            boolean newlyCreated
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sourceWalletId = Objects.requireNonNull(sourceWalletId, "sourceWalletId must not be null");
        this.destinationWalletId = Objects.requireNonNull(destinationWalletId, "destinationWalletId must not be null");
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.newlyCreated = newlyCreated;

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
            String requestHash,
            String reference
    ) {
        return Transfer.builder()
                .id(TransferId.newId())
                .tenantId(tenantId)
                .sourceWalletId(sourceWalletId)
                .destinationWalletId(destinationWalletId)
                .money(Money.of(amount, currency))
                .status(COMPLETED)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .reference(reference)
                .createdAt(Instant.now())
                .newlyCreated(true)
                .build();
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
        return completed(
                tenantId,
                sourceWalletId,
                destinationWalletId,
                amount,
                currency,
                idempotencyKey,
                "request-hash",
                reference
        );
    }

    public BigDecimal getAmount() {
        return money.amount();
    }

    public CurrencyCode getCurrency() {
        return money.currency();
    }
}
