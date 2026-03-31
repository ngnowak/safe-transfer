package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResponseMapper;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.wallet.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/transfers")
@Tag(name = "Transfer", description = "Money transfer endpoints")
public class TransferController {

    private final TransferService transferService;
    private final TransferResponseMapper transferResponseMapper;

    @PostMapping
    @Operation(summary = "Create wallet-to-wallet transfer")
    public TransferResponse createTransfer(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        var transfer = transferService.transfer(new TenantId(tenantId), idempotencyKey, request);
        return transferResponseMapper.toResponse(transfer);
    }
}