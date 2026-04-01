package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.application.exception.InsufficientFundsException;
import com.nn.safetransfer.transfer.application.exception.SameWalletTransferNotAllowedException;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;

@RequiredArgsConstructor
@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public Transfer transfer(TenantId tenantId, String idempotencyKey, CreateTransferRequest request) {
        return transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseGet(() -> createTransferSafely(tenantId, idempotencyKey, request));
    }

    private Transfer createTransferSafely(
            TenantId tenantId,
            String idempotencyKey,
            CreateTransferRequest request
    ) {
        var sourceWalletId = new WalletId(request.sourceWalletId());
        var destinationWalletId = new WalletId(request.destinationWalletId());

        if (sourceWalletId.equals(destinationWalletId)) {
            throw new SameWalletTransferNotAllowedException();
        }

        var requestCurrency = CurrencyCode.from(request.currency());

        Wallet firstLocked;
        Wallet secondLocked;

        if (sourceWalletId.value().compareTo(destinationWalletId.value()) < 0) {
            firstLocked = walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId)
                    .orElseThrow(() -> new WalletNotFoundException(sourceWalletId, tenantId));
            secondLocked = walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId)
                    .orElseThrow(() -> new WalletNotFoundException(destinationWalletId, tenantId));
        } else {
            firstLocked = walletRepository.findByIdAndTenantIdForUpdate(destinationWalletId, tenantId)
                    .orElseThrow(() -> new WalletNotFoundException(destinationWalletId, tenantId));
            secondLocked = walletRepository.findByIdAndTenantIdForUpdate(sourceWalletId, tenantId)
                    .orElseThrow(() -> new WalletNotFoundException(sourceWalletId, tenantId));
        }

        var sourceWallet = sourceWalletId.equals(firstLocked.getId()) ? firstLocked : secondLocked;
        var destinationWallet = destinationWalletId.equals(firstLocked.getId()) ? firstLocked : secondLocked;

        if (sourceWallet.getStatus() != ACTIVE) {
            throw new WalletOperationNotAllowedException("Source wallet must be ACTIVE");
        }
        if (destinationWallet.getStatus() != ACTIVE) {
            throw new WalletOperationNotAllowedException("Destination wallet must be ACTIVE");
        }
        if (sourceWallet.getCurrency() != requestCurrency) {
            throw new WalletCurrencyMismatchException(sourceWallet.getCurrency(), requestCurrency);
        }
        if (destinationWallet.getCurrency() != requestCurrency) {
            throw new WalletCurrencyMismatchException(destinationWallet.getCurrency(), requestCurrency);
        }

        var availableBalance = ledgerEntryRepository.calculateBalance(tenantId, sourceWalletId);
        if (availableBalance.compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(sourceWalletId, availableBalance, request.amount());
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

        return transfer;
    }
}
