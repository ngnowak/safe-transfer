package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.ledger.domain.LedgerEntryType;
import com.nn.safetransfer.outbox.application.OutboxEventFactory;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
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

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventFactory outboxEventFactory;

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
        assertAll(
                () -> assertThat(result.isSuccess()).isTrue(),
                () -> assertThat(result.getValue()).contains(existingTransfer)
        );
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
        given(outboxEventFactory.transferCompleted(any(Transfer.class)))
                .willAnswer(inv -> buildOutboxEvent(tenantId, inv.getArgument(0)));
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        var result = transferService.transfer(tenantId, idempotencyKey, request);

        // then
        assertThat(result.isSuccess()).isTrue();
        var transfer = result.getValue().orElseThrow();
        assertAll(
                () -> assertThat(transfer).isNotNull(),
                () -> assertThat(transfer.getAmount()).isEqualByComparingTo(amount),
                () -> assertThat(transfer.getCurrency()).isEqualTo(EUR)
        );

        verify(transferRepository).save(any(Transfer.class));

        var ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());

        var ledgerEntries = ledgerCaptor.getAllValues();
        assertAll(
                () -> assertThat(ledgerEntries.get(0).getType()).isEqualTo(LedgerEntryType.DEBIT),
                () -> assertThat(ledgerEntries.get(1).getType()).isEqualTo(LedgerEntryType.CREDIT)
        );
        verify(outboxEventFactory).transferCompleted(transfer);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldReturnFailureWhenSourceAndDestinationWalletsAreTheSame() {
        // given
        var tenantId = TenantId.create();
        var walletId = UUID.randomUUID();
        var request = new CreateTransferRequest(
                walletId, walletId,
                new BigDecimal("10.00"), "EUR", null
        );

        given(transferRepository.findByTenantIdAndIdempotencyKey(tenantId, "key"))
                .willReturn(Optional.empty());

        // when
        var result = transferService.transfer(tenantId, "key", request);

        // then
        assertAll(
                () -> assertThat(result.isFailure()).isTrue(),
                () -> assertThat(result.getError()).containsInstanceOf(TransferError.SameWalletTransfer.class)
        );
        verify(walletRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
    }

    @Test
    void shouldReturnFailureWhenSourceWalletNotFound() {
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

        // when
        var result = transferService.transfer(tenantId, "key", request);

        // then
        assertAll(
                () -> assertThat(result.isFailure()).isTrue(),
                () -> assertThat(result.getError()).containsInstanceOf(TransferError.WalletNotFound.class)
        );
    }

    @Test
    void shouldReturnFailureWhenSourceWalletIsNotActive() {
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

        // when
        var result = transferService.transfer(tenantId, "key", request);

        // then
        assertAll(
                () -> assertThat(result.isFailure()).isTrue(),
                () -> assertThat(result.getError()).containsInstanceOf(TransferError.WalletNotActive.class),
                () -> assertThat(result.getError().orElseThrow().getMessage()).contains("Source wallet must be ACTIVE")
        );
    }

    @Test
    void shouldReturnFailureWhenSourceWalletCurrencyDoesNotMatchRequest() {
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

        // when
        var result = transferService.transfer(tenantId, "key", request);

        // then
        assertAll(
                () -> assertThat(result.isFailure()).isTrue(),
                () -> assertThat(result.getError()).containsInstanceOf(TransferError.CurrencyMismatch.class)
        );
    }

    @Test
    void shouldReturnFailureWhenInsufficientFunds() {
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

        // when
        var result = transferService.transfer(tenantId, "key", request);

        // then
        assertAll(
                () -> assertThat(result.isFailure()).isTrue(),
                () -> assertThat(result.getError()).containsInstanceOf(TransferError.InsufficientFunds.class),
                () -> assertThat(result.getError().orElseThrow().getMessage()).contains("100").contains("500")
        );
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
        assertAll(
                () -> assertThat(result.isSuccess()).isTrue(),
                () -> assertThat(result.getValue()).contains(existingTransfer)
        );
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
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transferService.transfer(tenantId, "key", request))
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

    private OutboxEvent buildOutboxEvent(TenantId tenantId, Transfer transfer) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId.value())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(transfer.getId().value())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"%s\"}".formatted(transfer.getId().value()))
                .status(OutboxStatus.NEW)
                .occurredAt(java.time.Instant.now())
                .retryCount(0)
                .correlationId(transfer.getId().value().toString())
                .causationId(transfer.getIdempotencyKey())
                .build();
    }
}
