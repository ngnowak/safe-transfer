package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.metrics.TransferMetrics;
import com.nn.safetransfer.common.metrics.TransferMetricOutcome;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.outbox.application.OutboxEventFactory;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
import com.nn.safetransfer.transfer.domain.event.TransferCompletedDomainEvent;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;

@Slf4j
@RequiredArgsConstructor
@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final TransferMetrics transferMetrics;

    @Transactional
    public Result<TransferError, Transfer> transfer(TenantId tenantId, String idempotencyKey, CreateTransferRequest request) {
        var sample = transferMetrics.startTransfer();
        log.info("Processing transfer: tenantId={}, idempotencyKey={}, source={}, destination={}, amount={}, currency={}",
                tenantId, idempotencyKey, request.sourceWalletId(), request.destinationWalletId(), request.amount(), request.currency());

        var result = transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .<Result<TransferError, Transfer>>map(existing -> {
                    log.info("Returning existing transfer for idempotencyKey={}: transferId={}", idempotencyKey, existing.getId());
                    return Result.success(existing);
                })
                .orElseGet(() -> createTransferSafely(tenantId, idempotencyKey, request));

        if (result.isSuccess()) {
            transferMetrics.recordTransferSuccess(sample);
        } else {
            transferMetrics.recordTransferFailure(sample, metricOutcome(result.getError().orElseThrow()));
        }

        return result;
    }

    private Result<TransferError, Transfer> createTransferSafely(
            TenantId tenantId,
            String idempotencyKey,
            CreateTransferRequest request
    ) {
        var sourceWalletId = new WalletId(request.sourceWalletId());
        var destinationWalletId = new WalletId(request.destinationWalletId());

        if (sourceWalletId.equals(destinationWalletId)) {
            return Result.failure(new TransferError.SameWalletTransfer());
        }

        var requestCurrency = CurrencyCode.from(request.currency());

        Wallet firstLocked;
        Wallet secondLocked;

        if (sourceWalletId.value().compareTo(destinationWalletId.value()) < 0) {
            var first = walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId);
            if (first.isEmpty()) {
                return Result.failure(new TransferError.WalletNotFound(sourceWalletId, tenantId));
            }
            firstLocked = first.get();

            var second = walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId);
            if (second.isEmpty()) {
                return Result.failure(new TransferError.WalletNotFound(destinationWalletId, tenantId));
            }
            secondLocked = second.get();
        } else {
            var first = walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId);
            if (first.isEmpty()) {
                return Result.failure(new TransferError.WalletNotFound(destinationWalletId, tenantId));
            }
            firstLocked = first.get();

            var second = walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId);
            if (second.isEmpty()) {
                return Result.failure(new TransferError.WalletNotFound(sourceWalletId, tenantId));
            }
            secondLocked = second.get();
        }

        var sourceWallet = sourceWalletId.equals(firstLocked.getId()) ? firstLocked : secondLocked;
        var destinationWallet = destinationWalletId.equals(firstLocked.getId()) ? firstLocked : secondLocked;

        if (sourceWallet.getStatus() != ACTIVE) {
            return Result.failure(new TransferError.WalletNotActive("Source wallet must be ACTIVE"));
        }
        if (destinationWallet.getStatus() != ACTIVE) {
            return Result.failure(new TransferError.WalletNotActive("Destination wallet must be ACTIVE"));
        }
        if (sourceWallet.getCurrency() != requestCurrency) {
            return Result.failure(new TransferError.CurrencyMismatch(sourceWallet.getCurrency(), requestCurrency));
        }
        if (destinationWallet.getCurrency() != requestCurrency) {
            return Result.failure(new TransferError.CurrencyMismatch(destinationWallet.getCurrency(), requestCurrency));
        }

        var availableBalance = ledgerEntryRepository.calculateBalance(tenantId, sourceWalletId);
        if (availableBalance.compareTo(request.amount()) < 0) {
            log.warn("Insufficient funds: walletId={}, available={}, requested={}", sourceWalletId, availableBalance, request.amount());
            return Result.failure(new TransferError.InsufficientFunds(sourceWalletId, availableBalance, request.amount()));
        }

        Transfer transfer;
        try {
            transfer = transferRepository.save(
                    Transfer.completed(
                            tenantId,
                            sourceWalletId,
                            destinationWalletId,
                            request.amount(),
                            requestCurrency,
                            idempotencyKey,
                            request.reference()
                    )
            );


        } catch (DataIntegrityViolationException ex) {
            return transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .<Result<TransferError, Transfer>>map(Result::success)
                    .orElseThrow(() -> ex);
        }

        var ledgerReference = "Transfer " + transfer.getId().value();

        ledgerEntryRepository.save(
                LedgerEntry.debit(
                        tenantId,
                        sourceWalletId,
                        request.amount(),
                        requestCurrency,
                        ledgerReference
                )
        );

        ledgerEntryRepository.save(
                LedgerEntry.credit(
                        tenantId,
                        destinationWalletId,
                        request.amount(),
                        requestCurrency,
                        ledgerReference
                )
        );

        var transferCompletedEvent = TransferCompletedDomainEvent.from(transfer);
        outboxEventRepository.save(outboxEventFactory.from(transferCompletedEvent));

        log.info("Transfer completed: transferId={}, source={}, destination={}, amount={}",
                transfer.getId(), sourceWalletId, destinationWalletId, request.amount());

        return Result.success(transfer);
    }

    private TransferMetricOutcome metricOutcome(TransferError error) {
        return TransferMetricOutcome.from(error);
    }
}
