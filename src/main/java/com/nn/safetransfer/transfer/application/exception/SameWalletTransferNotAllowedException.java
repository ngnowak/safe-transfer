package com.nn.safetransfer.transfer.application.exception;

public class SameWalletTransferNotAllowedException extends RuntimeException {

    public SameWalletTransferNotAllowedException() {
        super("Source and destination wallets must be different");
    }
}
