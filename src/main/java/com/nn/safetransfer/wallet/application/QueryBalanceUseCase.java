package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;

public interface QueryBalanceUseCase {

    Result<WalletError, BalanceResult> handle(GetBalanceQuery query);
}
