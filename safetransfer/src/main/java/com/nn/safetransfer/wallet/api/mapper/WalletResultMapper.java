package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.application.WalletError;
import com.nn.safetransfer.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@Component
public class WalletResultMapper {
    private final WalletResponseMapper walletResponseMapper;

    public WalletResponse toWalletResponse(Result<WalletError, Wallet> result) {
        return switch (result) {
            case Result.Success<WalletError, Wallet> success ->
                mapSuccessResponse(success, walletResponseMapper::toWalletResponse).getBody();
            case Result.Failure<WalletError, ?> failure ->
                throw mapToResponseStatusException(failure);
        };
    }

    private <T, R> ResponseEntity<R> mapSuccessResponse(final Result<?, T> result,
                                                        final Function<T, R> responseCreator) {
        return result.getValue()
                .map(responseCreator)
                .map(ResponseEntity::ok)
                .orElseGet(this::emptySuccessResponseEntity);
    }

    private <T> ResponseEntity<T> emptySuccessResponseEntity() {
        return ResponseEntity.noContent().build();
    }

    private ResponseStatusException mapToResponseStatusException(final Result<WalletError, ?> result) {
        var error = result.getError().orElseGet(() -> new WalletError.OtherError(new IllegalStateException("The unknown error occurred")));
        var errorMessage = error.getMessage();

        return switch (error) {
            case WalletError.DuplicateWallet _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case WalletError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
