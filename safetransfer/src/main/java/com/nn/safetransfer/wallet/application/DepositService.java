package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
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

    // TODO return result
    @Transactional
    public LedgerEntry deposit(
            TenantId tenantId,
            WalletId walletId,
            DepositRequest request
    ) {
        log.info("Processing deposit: walletId={}, tenantId={}, amount={}, currency={}", walletId, tenantId, request.amount(), request.currency());

        var wallet = walletRepository.findByIdAndTenantId(walletId, tenantId)
                .orElseThrow(() -> {
                    log.warn("Wallet not found for deposit: walletId={}, tenantId={}", walletId, tenantId);
                    return new WalletNotFoundException(walletId, tenantId);
                });

        var requestCurrency = CurrencyCode.from(request.currency());

        wallet.ensureCanAcceptDeposit(requestCurrency);

        var entry = LedgerEntry.credit(
                tenantId,
                walletId,
                request.amount(),
                requestCurrency,
                request.reference()
        );

        var saved = ledgerEntryRepository.save(entry);
        log.info("Deposit completed: ledgerEntryId={}, walletId={}", saved.getId(), walletId);

        return saved;
    }
}
