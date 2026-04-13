package com.nn.safetransfer.ledger.domain;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import static com.nn.safetransfer.ledger.domain.LedgerEntryType.CREDIT;
import static com.nn.safetransfer.ledger.domain.LedgerEntryType.DEBIT;

@Getter
public class LedgerEntry {

    private final LedgerEntryId id;
    private final TenantId tenantId;
    private final WalletId walletId;
    private final LedgerEntryType type;
    private final Money money;
    private final String reference;
    private final Instant createdAt;

    @Builder
    public LedgerEntry(
            LedgerEntryId id,
            TenantId tenantId,
            WalletId walletId,
            LedgerEntryType type,
            Money money,
            String reference,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.walletId = Objects.requireNonNull(walletId, "walletId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        this.reference = reference;
    }

    public static LedgerEntry credit(
            TenantId tenantId,
            WalletId walletId,
            BigDecimal amount,
            CurrencyCode currency,
            String reference
    ) {
        return LedgerEntry.builder()
                .id(LedgerEntryId.newId())
                .tenantId(tenantId)
                .walletId(walletId)
                .type(CREDIT)
                .money(Money.of(amount, currency))
                .reference(reference)
                .createdAt(Instant.now())
                .build();
    }

    public static LedgerEntry debit(
            TenantId tenantId,
            WalletId walletId,
            BigDecimal amount,
            CurrencyCode currency,
            String reference
    ) {
        return LedgerEntry.builder()
                .id(LedgerEntryId.newId())
                .tenantId(tenantId)
                .walletId(walletId)
                .type(DEBIT)
                .money(Money.of(amount, currency))
                .reference(reference)
                .createdAt(Instant.now())
                .build();
    }

    public BigDecimal getAmount() {
        return money.amount();
    }

    public CurrencyCode getCurrency() {
        return money.currency();
    }
}
