package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResultMapper;
import com.nn.safetransfer.transfer.application.GetTransferQuery;
import com.nn.safetransfer.transfer.application.QueryTransferUseCase;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.wallet.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/transfers")
@Tag(name = "Transfer", description = "Money transfer endpoints")
public class TransferController {

    private final TransferService transferService;
    private final QueryTransferUseCase queryTransferUseCase;
    private final TransferResultMapper transferResultMapper;

    @PostMapping
    @Operation(summary = "Create wallet-to-wallet transfer")
    public ResponseEntity<TransferResponse> createTransfer(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        log.debug("Transfer request: tenantId={}, idempotencyKey={}, source={}, destination={}", tenantId, idempotencyKey, request.sourceWalletId(), request.destinationWalletId());
        var result = transferService.transfer(new TenantId(tenantId), idempotencyKey, request);

        return transferResultMapper.toTransferResponse(result);
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer by id")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID tenantId,
            @PathVariable UUID transferId
    ) {
        log.debug("Get transfer request: tenantId={}, transferId={}", tenantId, transferId);
        var query = GetTransferQuery.builder()
                .tenantId(new TenantId(tenantId))
                .transferId(new TransferId(transferId))
                .build();
        var result = queryTransferUseCase.handle(query);
        return transferResultMapper.toTransferResponse(result);
    }
}
