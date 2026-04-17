package com.nn.safetransfer.e2e.client;

import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class TransferApiClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/%s/transfers";
    private static final String TRANSFER_BY_ID_PATH = TRANSFERS_PATH + "/%s";

    private final E2eHttpClient httpClient;

    public TransferApiClient(E2eHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public TransferResponse createTransfer(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey
    ) throws IOException, InterruptedException {
        return createTransfer(tenantId, request, idempotencyKey, HttpStatus.CREATED);
    }

    public TransferResponse createIdempotentTransfer(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey
    ) throws IOException, InterruptedException {
        return createTransfer(tenantId, request, idempotencyKey, HttpStatus.OK);
    }

    private TransferResponse createTransfer(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey,
            HttpStatus expectedStatus
    ) throws IOException, InterruptedException {
        return httpClient.post(
                TRANSFERS_PATH.formatted(tenantId),
                request,
                Map.of(IDEMPOTENCY_KEY_HEADER, idempotencyKey),
                expectedStatus.value(),
                TransferResponse.class
        );
    }

    public ErrorDto createTransferError(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey
    ) throws IOException, InterruptedException {
        return httpClient.post(
                TRANSFERS_PATH.formatted(tenantId),
                request,
                Map.of(IDEMPOTENCY_KEY_HEADER, idempotencyKey),
                HttpStatus.CONFLICT.value(),
                ErrorDto.class
        );
    }

    public ErrorDto createTransferBadRequest(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey
    ) throws IOException, InterruptedException {
        return httpClient.post(
                TRANSFERS_PATH.formatted(tenantId),
                request,
                Map.of(IDEMPOTENCY_KEY_HEADER, idempotencyKey),
                HttpStatus.BAD_REQUEST.value(),
                ErrorDto.class
        );
    }

    public ErrorDto createTransferNotFound(
            UUID tenantId,
            CreateTransferRequest request,
            String idempotencyKey
    ) throws IOException, InterruptedException {
        return httpClient.post(
                TRANSFERS_PATH.formatted(tenantId),
                request,
                Map.of(IDEMPOTENCY_KEY_HEADER, idempotencyKey),
                HttpStatus.NOT_FOUND.value(),
                ErrorDto.class
        );
    }

    public TransferResponse getTransfer(UUID tenantId, UUID transferId) throws IOException, InterruptedException {
        return httpClient.get(TRANSFER_BY_ID_PATH.formatted(tenantId, transferId), HttpStatus.OK.value(), TransferResponse.class);
    }
}
