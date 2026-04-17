package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.api.mapper.AbstractResultMapper;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.application.WalletError;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class DepositResponseMapper extends AbstractResultMapper<WalletError, LedgerEntry, DepositResponse> {

    public DepositResponse toDepositResponse(Result<WalletError, LedgerEntry> result) {
        return mapResult(result, this::mapDepositResponse);
    }

    private DepositResponse mapDepositResponse(LedgerEntry ledgerEntry) {
        return DepositResponse.builder()
                .ledgerEntryId(ledgerEntry.getId().toString())
                .walletId(ledgerEntry.getWalletId().toString())
                .amount(ledgerEntry.getAmount())
                .currency(ledgerEntry.getCurrency().name())
                .entryType(ledgerEntry.getType().name())
                .reference(ledgerEntry.getReference())
                .createdAt(ledgerEntry.getCreatedAt())
                .build();
    }

    @Override
    protected ResponseStatusException mapFailure(WalletError error) {
        var errorMessage = error.getMessage();
        return switch (error) {
            case WalletError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            case WalletError.WalletNotActive _ -> new ResponseStatusException(CONFLICT, errorMessage);
            case WalletError.CurrencyMismatch _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
