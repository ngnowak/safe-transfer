package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class DepositService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public Result<WalletError, LedgerEntry> deposit(
            TenantId tenantId,
            WalletId walletId,
            DepositCommand command
    ) {
        log.info("Processing deposit: walletId={}, tenantId={}, amount={}, currency={}", walletId, tenantId, command.amount(), command.currency());

        return walletRepository.findByIdAndTenantId(walletId, tenantId)
                .map(wallet -> processDeposit(tenantId, walletId, command, wallet))
                .orElseGet(() -> {
                    log.warn("Wallet not found for deposit: walletId={}, tenantId={}", walletId, tenantId);
                    return Result.failure(new WalletError.WalletNotFound(walletId, tenantId));
                });
    }

    private Result<WalletError, LedgerEntry> processDeposit(
            TenantId tenantId,
            WalletId walletId,
            DepositCommand command,
            Wallet wallet
    ) {
        var requestCurrency = CurrencyCode.from(command.currency());

        try {
            wallet.ensureCanAcceptDeposit(requestCurrency);
        } catch (WalletCurrencyMismatchException ex) {
            return Result.failure(new WalletError.CurrencyMismatch(wallet.getCurrency(), requestCurrency));
        } catch (WalletOperationNotAllowedException ex) {
            return Result.failure(new WalletError.WalletNotActive(ex.getMessage()));
        }

        var entry = LedgerEntry.credit(
                tenantId,
                walletId,
                command.amount(),
                requestCurrency,
                command.reference()
        );

        var saved = ledgerEntryRepository.save(entry);
        log.info("Deposit completed: ledgerEntryId={}, walletId={}", saved.getId(), walletId);

        return Result.success(saved);
    }
}
