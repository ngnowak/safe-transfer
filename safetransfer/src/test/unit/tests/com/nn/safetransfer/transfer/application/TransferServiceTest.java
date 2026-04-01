package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.ledger.domain.LedgerEntryType;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.application.exception.InsufficientFundsException;
import com.nn.safetransfer.transfer.application.exception.SameWalletTransferNotAllowedException;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.WalletStatus;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void shouldReturnExistingTransferWhenIdempotencyKeyAlreadyExists() {
        // given
        var tenantId = TenantId.create();
        var idempotencyKey = "existing-key";
        var existingTransfer = createTestTransfer(tenantId);
        var request = createTransferRequest();

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .willReturn(Optional.of(existingTransfer));

        // when
        var result = transferService.transfer(tenantId, idempotencyKey, request);

        // then
        assertThat(result).isEqualTo(existingTransfer);
        verifyNoInteractions(walletRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void shouldExecuteTransferSuccessfully() {
        // given
        var tenantId = TenantId.create();
        var idempotencyKey = "new-key";
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var sourceWallet = buildWallet(sourceWalletId, tenantId, EUR);
        var destinationWallet = buildWallet(destinationWalletId, tenantId, EUR);
        var amount = new BigDecimal("50.00");

        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                amount, "EUR", "Payment"
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .willReturn(Optional.empty());
        given(walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId))
                .willReturn(Optional.of(sourceWallet));
        given(walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId))
                .willReturn(Optional.of(destinationWallet));
        given(ledgerEntryRepository.calculateBalance(tenantId, sourceWalletId))
                .willReturn(new BigDecimal("200.00"));
        given(transferRepository.save(any(Transfer.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        var result = transferService.transfer(tenantId, idempotencyKey, request);

        // then
        assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getAmount()).isEqualByComparingTo(amount),
                () -> assertThat(result.getCurrency()).isEqualTo(EUR)
        );

        verify(transferRepository).save(any(Transfer.class));

        var ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());

        var ledgerEntries = ledgerCaptor.getAllValues();
        assertAll(
                () -> assertThat(ledgerEntries.get(0).getType()).isEqualTo(LedgerEntryType.DEBIT),
                () -> assertThat(ledgerEntries.get(1).getType()).isEqualTo(LedgerEntryType.CREDIT)
        );
    }

    @Test
    void shouldThrowWhenSourceAndDestinationWalletsAreTheSame() {
        // given
        var tenantId = TenantId.create();
        var walletId = UUID.randomUUID();
        var request = new CreateTransferRequest(
                walletId, walletId,
                new BigDecimal("10.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(SameWalletTransferNotAllowedException.class);

        verify(walletRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
    }

    @Test
    void shouldThrowWhenSourceWalletNotFound() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                new BigDecimal("10.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());
        given(walletRepository.findByIdAndTenantIdForUpdate(any(WalletId.class), eq(tenantId)))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void shouldThrowWhenSourceWalletIsNotActive() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var sourceWallet = buildWallet(sourceWalletId, tenantId, EUR);
        sourceWallet.block();
        var destinationWallet = buildWallet(destinationWalletId, tenantId, EUR);

        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                new BigDecimal("10.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());
        given(walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId))
                .willReturn(Optional.of(sourceWallet));
        given(walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId))
                .willReturn(Optional.of(destinationWallet));

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(WalletOperationNotAllowedException.class)
                .hasMessageContaining("Source wallet must be ACTIVE");
    }

    @Test
    void shouldThrowWhenSourceWalletCurrencyDoesNotMatchRequest() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var sourceWallet = buildWallet(sourceWalletId, tenantId, USD);
        var destinationWallet = buildWallet(destinationWalletId, tenantId, EUR);

        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                new BigDecimal("10.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());
        given(walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId))
                .willReturn(Optional.of(sourceWallet));
        given(walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId))
                .willReturn(Optional.of(destinationWallet));

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(WalletCurrencyMismatchException.class);
    }

    @Test
    void shouldThrowWhenInsufficientFunds() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var sourceWallet = buildWallet(sourceWalletId, tenantId, EUR);
        var destinationWallet = buildWallet(destinationWalletId, tenantId, EUR);

        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                new BigDecimal("500.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());
        given(walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId))
                .willReturn(Optional.of(sourceWallet));
        given(walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId))
                .willReturn(Optional.of(destinationWallet));
        given(ledgerEntryRepository.calculateBalance(tenantId, sourceWalletId))
                .willReturn(new BigDecimal("100.00"));

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("500");
    }

    @Test
    void shouldHandleDataIntegrityViolationByReturningExistingTransfer() {
        // given
        var tenantId = TenantId.create();
        var idempotencyKey = "race-key";
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var sourceWallet = buildWallet(sourceWalletId, tenantId, EUR);
        var destinationWallet = buildWallet(destinationWalletId, tenantId, EUR);
        var existingTransfer = createTestTransfer(tenantId);

        var request = new CreateTransferRequest(
                sourceWalletId.value(), destinationWalletId.value(),
                new BigDecimal("50.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existingTransfer));
        given(walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId))
                .willReturn(Optional.of(sourceWallet));
        given(walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId))
                .willReturn(Optional.of(destinationWallet));
        given(ledgerEntryRepository.calculateBalance(tenantId, sourceWalletId))
                .willReturn(new BigDecimal("200.00"));
        given(transferRepository.save(any(Transfer.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate idempotency key"));

        // when
        var result = transferService.transfer(tenantId, idempotencyKey, request);

        // then
        assertThat(result).isEqualTo(existingTransfer);
    }

    @Test
    void shouldThrowWhenInvalidCurrencyCodeInRequest() {
        // given
        var tenantId = TenantId.create();
        var request = new CreateTransferRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "INVALID", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    private Transfer createTestTransfer(TenantId tenantId) {
        return Transfer.completed(
                tenantId,
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("50.00"),
                EUR,
                "test-key",
                "Test transfer"
        );
    }

    private CreateTransferRequest createTransferRequest() {
        return new CreateTransferRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50.00"), "EUR", "Payment"
        );
    }

    private Wallet buildWallet(WalletId walletId, TenantId tenantId, CurrencyCode currency) {
        return Wallet.builder()
                .id(walletId)
                .tenantId(tenantId)
                .customerId(CustomerId.create())
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .createdAt(java.time.Instant.now())
                .build();
    }
}
