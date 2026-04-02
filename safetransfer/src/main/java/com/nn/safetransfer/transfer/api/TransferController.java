package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResponseMapper;
import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.wallet.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/transfers")
@Tag(name = "Transfer", description = "Money transfer endpoints")
public class TransferController {

    private final TransferService transferService;
    private final TransferResponseMapper transferResponseMapper;

    @PostMapping
    @Operation(summary = "Create wallet-to-wallet transfer")
    public ResponseEntity<?> createTransfer(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        log.info("Transfer request: tenantId={}, idempotencyKey={}, source={}, destination={}", tenantId, idempotencyKey, request.sourceWalletId(), request.destinationWalletId());
        var result = transferService.transfer(new TenantId(tenantId), idempotencyKey, request);

        return switch (result) {
            case Result.Success<TransferError, ?> success -> {
                var transfer = (com.nn.safetransfer.transfer.domain.Transfer) success.value();
                log.info("Transfer response: transferId={}, status={}", transfer.getId(), transfer.getStatus());
                yield ResponseEntity.ok(transferResponseMapper.toResponse(transfer));
            }
            case Result.Failure<TransferError, ?> failure -> {
                log.warn("Transfer failed: {}", failure.error().getMessage());
                var problem = toErrorResponse(failure.error());
                yield ResponseEntity.status(problem.getStatus()).body(problem);
            }
        };
    }

    private ProblemDetail toErrorResponse(TransferError error) {
        return switch (error) {
            case TransferError.SameWalletTransfer e -> {
                var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
                problem.setTitle("Invalid transfer");
                yield problem;
            }
            case TransferError.WalletNotFound e -> {
                var problem = ProblemDetail.forStatusAndDetail(NOT_FOUND, e.getMessage());
                problem.setTitle("Wallet not found");
                problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-not-found"));
                yield problem;
            }
            case TransferError.WalletNotActive e -> {
                var problem = ProblemDetail.forStatusAndDetail(CONFLICT, e.getMessage());
                problem.setTitle("Wallet operation not allowed");
                problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-operation-not-allowed"));
                yield problem;
            }
            case TransferError.CurrencyMismatch e -> {
                var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
                problem.setTitle("Wallet currency mismatch");
                problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-currency-mismatch"));
                yield problem;
            }
            case TransferError.InsufficientFunds e -> {
                var problem = ProblemDetail.forStatusAndDetail(CONFLICT, e.getMessage());
                problem.setTitle("Insufficient funds");
                yield problem;
            }
        };
    }
}
