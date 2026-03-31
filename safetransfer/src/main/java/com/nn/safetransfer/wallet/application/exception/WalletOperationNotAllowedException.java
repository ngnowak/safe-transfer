package com.nn.safetransfer.wallet.application.exception;

public class WalletOperationNotAllowedException extends RuntimeException {

    public WalletOperationNotAllowedException(String message) {
        super(message);
    }
}

