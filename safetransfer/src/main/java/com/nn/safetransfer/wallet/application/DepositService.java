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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DepositService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;


    @Transactional
    public LedgerEntry deposit(
            TenantId tenantId,
            WalletId walletId,
            DepositRequest request
    ) {
        var wallet = walletRepository.findByIdAndTenantId(walletId, tenantId)
                .orElseThrow(() -> new WalletNotFoundException(walletId, tenantId));

        var requestCurrency = CurrencyCode.from(request.currency());

        wallet.ensureCanAcceptDeposit(requestCurrency);

        var entry = LedgerEntry.credit(
                tenantId,
                walletId,
                request.amount(),
                requestCurrency,
                request.reference()
        );

        return ledgerEntryRepository.save(entry);
    }
}
