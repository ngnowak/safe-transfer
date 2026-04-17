package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.api.mapper.AbstractResultMapper;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.application.BalanceResult;
import com.nn.safetransfer.wallet.application.WalletError;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class BalanceResponseMapper extends AbstractResultMapper<WalletError, BalanceResult, BalanceResponse> {

    public BalanceResponse toBalanceResponse(Result<WalletError, BalanceResult> result) {
        return mapResult(result, this::mapBalanceResponse);
    }

    private BalanceResponse mapBalanceResponse(BalanceResult result) {
        return BalanceResponse.builder()
                .walletId(result.wallet().getId().toString())
                .tenantId(result.wallet().getTenantId().toString())
                .currency(result.wallet().getCurrency().name())
                .balance(result.balance())
                .build();
    }

    @Override
    protected ResponseStatusException mapFailure(WalletError error) {
        var errorMessage = error.getMessage();
        return switch (error) {
            case WalletError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
