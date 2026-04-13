package com.nn.safetransfer.transfer.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.transfer.domain.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

@RequiredArgsConstructor
@Component
public class TransferResultMapper {
    private final TransferResponseMapper transferResponseMapper;

    public ResponseEntity<TransferResponse> toTransferResponse(Result<TransferError, Transfer> result) {
        return switch (result) {
            case Result.Success<TransferError, Transfer> success ->
                mapSuccessResponse(success, transferResponseMapper::toResponse);
            case Result.Failure<TransferError, ?> failure ->
                throw mapToResponseStatusException(failure);
        };
    }

    private <T, R> ResponseEntity<R> mapSuccessResponse(final Result<?, T> result,
                                                        final Function<T, R> responseCreator) {
        return result.getValue()
                .map(value -> {
                    var status = value instanceof Transfer transfer && transfer.isNewlyCreated() ? CREATED : OK;
                    return ResponseEntity.status(status).body(responseCreator.apply(value));
                })
                .orElseGet(this::emptySuccessResponseEntity);
    }

    private <T> ResponseEntity<T> emptySuccessResponseEntity() {
        return ResponseEntity.noContent().build();
    }

    private ResponseStatusException mapToResponseStatusException(final Result<TransferError, ?> result) {
        var error = result.getError().orElseGet(() -> new TransferError.OtherError(new IllegalStateException("The unknown error occurred")));
        var errorMessage = error.getMessage();

        return switch (error) {
            case TransferError.SameWalletTransfer _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case TransferError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            case TransferError.WalletNotActive _ -> new ResponseStatusException(CONFLICT, errorMessage);
            case TransferError.CurrencyMismatch _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case TransferError.InsufficientFunds _ -> new ResponseStatusException(CONFLICT, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
