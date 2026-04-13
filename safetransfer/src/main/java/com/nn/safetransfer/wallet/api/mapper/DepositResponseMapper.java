package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.application.WalletError;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class DepositResponseMapper {

    public DepositResponse toDepositResponse(Result<WalletError, LedgerEntry> result) {
        return switch (result) {
            case Result.Success<WalletError, LedgerEntry> success ->
                mapSuccessResponse(success, this::mapDepositResponse);
            case Result.Failure<WalletError, ?> failure ->
                throw mapToResponseStatusException(failure);
        };
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

    private <T, R> R mapSuccessResponse(Result<?, T> result, Function<T, R> responseCreator) {
        return result.getValue()
                .map(responseCreator)
                .orElseThrow(() -> new IllegalStateException("Expected value for successful result"));
    }

    private ResponseStatusException mapToResponseStatusException(Result<WalletError, ?> result) {
        var error = result.getError().orElseGet(() -> new WalletError.OtherError(new IllegalStateException("The unknown error occurred")));
        var errorMessage = error.getMessage();

        return switch (error) {
            case WalletError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            case WalletError.WalletNotActive _ -> new ResponseStatusException(CONFLICT, errorMessage);
            case WalletError.CurrencyMismatch _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
