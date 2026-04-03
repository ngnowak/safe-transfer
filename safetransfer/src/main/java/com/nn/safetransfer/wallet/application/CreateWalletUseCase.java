package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.domain.Wallet;

public interface CreateWalletUseCase {
    Result<WalletError, Wallet> handle(CreateWalletCommand command);
}