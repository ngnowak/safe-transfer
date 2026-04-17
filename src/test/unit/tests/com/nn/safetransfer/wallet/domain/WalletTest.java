package com.nn.safetransfer.wallet.domain;

import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import org.junit.jupiter.api.Test;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.*;
import static com.nn.safetransfer.wallet.domain.WalletStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class WalletTest {

    @Test
    void shouldCreateWalletWithActiveStatus() {
        // given
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var currency = EUR;

        // when
        var wallet = Wallet.create(tenantId, customerId, currency);

        // then
        assertAll(
                () -> assertThat(wallet.getId()).isNotNull(),
                () -> assertThat(wallet.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(wallet.getCustomerId()).isEqualTo(customerId),
                () -> assertThat(wallet.getCurrency()).isEqualTo(currency),
                () -> assertThat(wallet.getStatus()).isEqualTo(ACTIVE),
                () -> assertThat(wallet.getCreatedAt()).isNotNull()
        );
    }

    @Test
    void shouldBlockActiveWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), PLN);

        // when
        wallet.block();

        // then
        assertThat(wallet.getStatus()).isEqualTo(BLOCKED);
    }

    @Test
    void shouldBlockBlockedWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), PLN);
        wallet.block();

        // when
        wallet.block();

        // then
        assertThat(wallet.getStatus()).isEqualTo(BLOCKED);
    }

    @Test
    void shouldThrowWhenBlockingClosedWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), PLN);
        wallet.close();

        // when / then
        assertThatThrownBy(wallet::block)
                .isInstanceOf(WalletOperationNotAllowedException.class)
                .hasMessage("Closed wallet cannot be blocked");
    }

    @Test
    void shouldCloseActiveWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), USD);

        // when
        wallet.close();

        // then
        assertThat(wallet.getStatus()).isEqualTo(CLOSED);
    }

    @Test
    void shouldCloseBlockedWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), USD);
        wallet.block();

        // when
        wallet.close();

        // then
        assertThat(wallet.getStatus()).isEqualTo(CLOSED);
    }

    @Test
    void shouldPassDepositValidationForActiveWalletWithMatchingCurrency() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), EUR);

        // when / then
        wallet.ensureCanAcceptDeposit(EUR);
    }

    @Test
    void shouldThrowWhenDepositOnBlockedWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), EUR);
        wallet.block();

        // when / then
        assertThatThrownBy(() -> wallet.ensureCanAcceptDeposit(EUR))
                .isInstanceOf(WalletOperationNotAllowedException.class)
                .hasMessage("Wallet must be ACTIVE to accept deposits");
    }

    @Test
    void shouldThrowWhenDepositOnClosedWallet() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), EUR);
        wallet.close();

        // when / then
        assertThatThrownBy(() -> wallet.ensureCanAcceptDeposit(EUR))
                .isInstanceOf(WalletOperationNotAllowedException.class)
                .hasMessage("Wallet must be ACTIVE to accept deposits");
    }

    @Test
    void shouldThrowWhenDepositCurrencyDoesNotMatchWalletCurrency() {
        // given
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), EUR);

        // when / then
        assertThatThrownBy(() -> wallet.ensureCanAcceptDeposit(USD))
                .isInstanceOf(WalletCurrencyMismatchException.class);
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        // when / then
        assertThatThrownBy(() -> Wallet.builder()
                .id(null)
                .tenantId(TenantId.create())
                .customerId(CustomerId.create())
                .currency(EUR)
                .status(ACTIVE)
                .createdAt(java.time.Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
