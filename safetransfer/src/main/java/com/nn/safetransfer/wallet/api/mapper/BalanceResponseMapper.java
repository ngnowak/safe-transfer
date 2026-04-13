package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.application.BalanceResult;
import com.nn.safetransfer.wallet.application.WalletError;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class BalanceResponseMapper {

    public BalanceResponse toBalanceResponse(Result<WalletError, BalanceResult> result) {
        return switch (result) {
            case Result.Success<WalletError, BalanceResult> success ->
                mapSuccessResponse(success, this::mapBalanceResponse);
            case Result.Failure<WalletError, ?> failure ->
                throw mapToResponseStatusException(failure);
        };
    }

    private BalanceResponse mapBalanceResponse(BalanceResult result) {
        return BalanceResponse.builder()
                .walletId(result.wallet().getId().toString())
                .tenantId(result.wallet().getTenantId().toString())
                .currency(result.wallet().getCurrency().name())
                .balance(result.balance())
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
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
