package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.ledger.domain.LedgerEntryType;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private DepositService depositService;

    @Test
    void shouldDepositSuccessfully() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var request = new DepositRequest(new BigDecimal("100.00"), "EUR", "Test deposit");
        var captor = ArgumentCaptor.forClass(LedgerEntry.class);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));
        given(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        var result = depositService.deposit(tenantId, walletId, request);

        // then
        verify(ledgerEntryRepository).save(captor.capture());
        var savedEntry = captor.getValue();

        assertAll(
                () -> assertThat(savedEntry.getType()).isEqualTo(LedgerEntryType.CREDIT),
                () -> assertThat(savedEntry.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(savedEntry.getWalletId()).isEqualTo(walletId),
                () -> assertThat(savedEntry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00")),
                () -> assertThat(savedEntry.getCurrency()).isEqualTo(EUR),
                () -> assertThat(savedEntry.getReference()).isEqualTo("Test deposit"),
                () -> assertThat(result).isEqualTo(savedEntry)
        );
    }

    @Test
    void shouldThrowWhenWalletNotFound() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var request = new DepositRequest(new BigDecimal("100.00"), "EUR", null);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> depositService.deposit(tenantId, walletId, request))
                .isInstanceOf(WalletNotFoundException.class);

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCurrencyDoesNotMatch() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var request = new DepositRequest(new BigDecimal("100.00"), "USD", null);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));

        // when / then
        assertThatThrownBy(() -> depositService.deposit(tenantId, walletId, request))
                .isInstanceOf(WalletCurrencyMismatchException.class);

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenWalletIsNotActive() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        wallet.block();
        var request = new DepositRequest(new BigDecimal("100.00"), "EUR", null);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));

        // when / then
        assertThatThrownBy(() -> depositService.deposit(tenantId, walletId, request))
                .isInstanceOf(WalletOperationNotAllowedException.class)
                .hasMessage("Wallet must be ACTIVE to accept deposits");

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCurrencyCodeIsInvalid() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var request = new DepositRequest(new BigDecimal("100.00"), "INVALID", null);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));

        // when / then
        assertThatThrownBy(() -> depositService.deposit(tenantId, walletId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");

        verify(ledgerEntryRepository, never()).save(any());
    }
}
