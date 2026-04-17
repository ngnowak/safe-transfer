package com.nn.safetransfer.wallet.domain;

import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

import static com.nn.safetransfer.wallet.domain.WalletStatus.*;

@Getter
public class Wallet {

    private final WalletId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final CurrencyCode currency;
    private WalletStatus status;
    private final Instant createdAt;

    @Builder
    private Wallet(
            WalletId id,
            TenantId tenantId,
            CustomerId customerId,
            CurrencyCode currency,
            WalletStatus status,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Wallet create(
            TenantId tenantId,
            CustomerId customerId,
            CurrencyCode currency
    ) {
        return Wallet.builder()
                .id(WalletId.create())
                .tenantId(tenantId)
                .customerId(customerId)
                .currency(currency)
                .status(ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    public void block() {
        if (status == CLOSED) {
            throw new WalletOperationNotAllowedException("Closed wallet cannot be blocked");
        }
        this.status = BLOCKED;
    }

    public void close() {
        this.status = CLOSED;
    }

    public void ensureCanAcceptDeposit(CurrencyCode currency) {
        if (status != ACTIVE) {
            throw new WalletOperationNotAllowedException("Wallet must be ACTIVE to accept deposits");
        }

        if (this.currency != currency) {
            throw new WalletCurrencyMismatchException(currency, this.currency);
        }
    }
}
