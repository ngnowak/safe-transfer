package com.nn.safetransfer.transfer.api.mapper;

import com.nn.safetransfer.common.api.mapper.AbstractResultMapper;
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
public class TransferResultMapper extends AbstractResultMapper<TransferError, Transfer, ResponseEntity<TransferResponse>> {
    private final TransferResponseMapper transferResponseMapper;

    public ResponseEntity<TransferResponse> toTransferResponse(Result<TransferError, Transfer> result) {
        return mapResult(result, transfer -> ResponseEntity.ok(transferResponseMapper.toResponse(transfer)));
    }

    @Override
    protected ResponseEntity<TransferResponse> mapSuccess(Result<?, Transfer> result,
                                                          Function<Transfer, ResponseEntity<TransferResponse>> successMapper) {
        return result.getValue()
                .map(transfer -> {
                    var status = transfer.isNewlyCreated() ? CREATED : OK;
                    return ResponseEntity.status(status).body(transferResponseMapper.toResponse(transfer));
                })
                .orElseGet(this::emptySuccessResponseEntity);
    }

    private <T> ResponseEntity<T> emptySuccessResponseEntity() {
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseStatusException mapFailure(TransferError error) {
        var errorMessage = error.getMessage();
        return switch (error) {
            case TransferError.SameWalletTransfer _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case TransferError.WalletNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            case TransferError.TransferNotFound _ -> new ResponseStatusException(NOT_FOUND, errorMessage);
            case TransferError.WalletNotActive _ -> new ResponseStatusException(CONFLICT, errorMessage);
            case TransferError.CurrencyMismatch _ -> new ResponseStatusException(BAD_REQUEST, errorMessage);
            case TransferError.InsufficientFunds _ -> new ResponseStatusException(CONFLICT, errorMessage);
            case TransferError.IdempotencyKeyConflict _ -> new ResponseStatusException(CONFLICT, errorMessage);
            default -> new ResponseStatusException(BAD_REQUEST, errorMessage);
        };
    }
}
