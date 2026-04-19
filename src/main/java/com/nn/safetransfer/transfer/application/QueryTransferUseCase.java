package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.domain.Transfer;

public interface QueryTransferUseCase {

    Result<TransferError, Transfer> handle(GetTransferQuery query);
}
