package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Empty;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;

public interface TransferPolicy {

    Result<TransferError, Empty> validate(CreateTransferRequest request);
}
