package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.Wallet;

public interface CreateWalletUseCase {
    Wallet handle(CreateWalletCommand command);
}