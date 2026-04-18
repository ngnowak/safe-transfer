package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Empty;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SingleTransferLimitPolicy implements TransferPolicy {

    private final TransferRiskProperties properties;

    @Override
    public Result<TransferError, Empty> validate(CreateTransferRequest request) {
        if (!properties.enabled()) {
            return Result.emptySuccessResult();
        }

        if (request.amount().compareTo(properties.maxSingleTransferAmount()) > 0) {
            return Result.failure(new TransferError.TransferLimitExceeded(
                    request.amount(),
                    properties.maxSingleTransferAmount()
            ));
        }

        return Result.emptySuccessResult();
    }
}
