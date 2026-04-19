package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResultMapper;
import com.nn.safetransfer.transfer.application.GetTransferQuery;
import com.nn.safetransfer.transfer.application.QueryTransferUseCase;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.wallet.domain.TenantId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY_BLANK_MESSAGE;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY_MAX_LENGTH;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY_TOO_LONG_MESSAGE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RequiredArgsConstructor
@RestController
public class TransferController implements TransferApi {

    private final TransferService transferService;
    private final QueryTransferUseCase queryTransferUseCase;
    private final TransferResultMapper transferResultMapper;

    @Override
    public ResponseEntity<TransferResponse> createTransfer(
            @PathVariable UUID tenantId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        var result = transferService.transfer(new TenantId(tenantId), idempotencyKey, request);

        return transferResultMapper.toTransferResponse(result);
    }

    @Override
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID tenantId,
            @PathVariable UUID transferId
    ) {
        var query = getGetTransferQuery(tenantId, transferId);
        var result = queryTransferUseCase.handle(query);
        return transferResultMapper.toTransferResponse(result);
    }

    private GetTransferQuery getGetTransferQuery(UUID tenantId, UUID transferId) {
        return GetTransferQuery.builder()
                .tenantId(new TenantId(tenantId))
                .transferId(new TransferId(transferId))
                .build();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(BAD_REQUEST, IDEMPOTENCY_KEY_BLANK_MESSAGE);
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, IDEMPOTENCY_KEY_TOO_LONG_MESSAGE);
        }
    }
}
