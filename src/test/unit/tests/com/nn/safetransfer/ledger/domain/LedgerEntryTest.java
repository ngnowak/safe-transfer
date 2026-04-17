package com.nn.safetransfer.ledger.domain;

import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static com.nn.safetransfer.ledger.domain.LedgerEntryType.CREDIT;
import static com.nn.safetransfer.ledger.domain.LedgerEntryType.DEBIT;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.PLN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class LedgerEntryTest {

    @Test
    void shouldCreateCreditEntry() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var amount = new BigDecimal("250.50");
        var reference = "Deposit ref";

        // when
        var entry = LedgerEntry.credit(tenantId, walletId, amount, PLN, reference);

        // then
        assertAll(
                () -> assertThat(entry.getId()).isNotNull(),
                () -> assertThat(entry.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entry.getWalletId()).isEqualTo(walletId),
                () -> assertThat(entry.getType()).isEqualTo(CREDIT),
                () -> assertThat(entry.getMoney()).isEqualTo(Money.of(amount, PLN)),
                () -> assertThat(entry.getAmount()).isEqualByComparingTo(amount),
                () -> assertThat(entry.getCurrency()).isEqualTo(PLN),
                () -> assertThat(entry.getReference()).isEqualTo(reference),
                () -> assertThat(entry.getCreatedAt()).isNotNull()
        );
    }

    @Test
    void shouldCreateDebitEntry() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var amount = new BigDecimal("100.00");
        var reference = "Transfer ref";

        // when
        var entry = LedgerEntry.debit(tenantId, walletId, amount, PLN, reference);

        // then
        assertAll(
                () -> assertThat(entry.getId()).isNotNull(),
                () -> assertThat(entry.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entry.getWalletId()).isEqualTo(walletId),
                () -> assertThat(entry.getType()).isEqualTo(DEBIT),
                () -> assertThat(entry.getMoney()).isEqualTo(Money.of(amount, PLN)),
                () -> assertThat(entry.getAmount()).isEqualByComparingTo(amount),
                () -> assertThat(entry.getCurrency()).isEqualTo(PLN),
                () -> assertThat(entry.getReference()).isEqualTo(reference),
                () -> assertThat(entry.getCreatedAt()).isNotNull()
        );
    }

    @Test
    void shouldCreateCreditEntryWithNullReference() {
        // when
        var entry = LedgerEntry.credit(TenantId.create(), WalletId.create(), new BigDecimal("10.00"), PLN, null);

        // then
        assertThat(entry.getReference()).isNull();
    }

    @Test
    void shouldThrowWhenAmountIsZero() {
        // when / then
        assertThatThrownBy(() -> LedgerEntry.credit(
                TenantId.create(), WalletId.create(), BigDecimal.ZERO, PLN, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        // when / then
        assertThatThrownBy(() -> LedgerEntry.debit(
                TenantId.create(), WalletId.create(), new BigDecimal("-5.00"), PLN, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenTenantIdIsNull() {
        // when / then
        assertThatThrownBy(() -> LedgerEntry.credit(
                null, WalletId.create(), new BigDecimal("10.00"), PLN, null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tenantId must not be null");
    }

    @Test
    void shouldThrowWhenWalletIdIsNull() {
        // when / then
        assertThatThrownBy(() -> LedgerEntry.credit(
                TenantId.create(), null, new BigDecimal("10.00"), PLN, null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("walletId must not be null");
    }

    @Test
    void shouldThrowWhenCurrencyIsNull() {
        // when / then
        assertThatThrownBy(() -> LedgerEntry.builder()
                .id(LedgerEntryId.newId())
                .tenantId(TenantId.create())
                .walletId(WalletId.create())
                .type(CREDIT)
                .money(null)
                .createdAt(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("money must not be null");
    }
}
