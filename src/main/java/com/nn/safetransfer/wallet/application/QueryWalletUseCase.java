package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.domain.Wallet;

public interface QueryWalletUseCase {

    Result<WalletError, Wallet> handle(GetWalletQuery query);
}
