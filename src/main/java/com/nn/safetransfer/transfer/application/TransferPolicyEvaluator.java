package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Empty;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.nn.safetransfer.common.domain.result.Result.emptySuccessResult;

@Component
@RequiredArgsConstructor
public class TransferPolicyEvaluator {

    private final List<TransferPolicy> policies;

    public Result<TransferError, Empty> validate(CreateTransferRequest request) {
        for (var policy : policies) {
            var result = policy.validate(request);
            if (result.isFailure()) {
                return result;
            }
        }

        return emptySuccessResult();
    }
}
