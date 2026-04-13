package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryTransferUseCase {

    private final TransferRepository transferRepository;

    @Transactional(readOnly = true)
    public Result<TransferError, Transfer> handle(GetTransferQuery query) {
        log.debug("Querying transfer: transferId={}, tenantId={}", query.transferId(), query.tenantId());

        return transferRepository.findByIdAndTenantId(query.transferId(), query.tenantId())
                .<Result<TransferError, Transfer>>map(Result::success)
                .orElseGet(() -> Result.failure(new TransferError.TransferNotFound(query.transferId(), query.tenantId())));
    }
}
