package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.api.mapper.AbstractResultMapper;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.application.WalletError;
import com.nn.safetransfer.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@Component
public class WalletResultMapper extends AbstractResultMapper<WalletError, Wallet, WalletResponse> {
    private final WalletResponseMapper walletResponseMapper;

    public WalletResponse toWalletResponse(Result<WalletError, Wallet> result) {
        return mapResult(result, walletResponseMapper::toWalletResponse);
    }

    @Override
    protected ResponseStatusException mapFailure(WalletError error) {
        var errorMessage = error.getMessage();
        return switch (error) {
            case WalletError.DuplicateWallet _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case WalletError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
