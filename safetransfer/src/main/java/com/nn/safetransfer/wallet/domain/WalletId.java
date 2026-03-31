package com.nn.safetransfer.wallet.domain;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public record WalletId(UUID value) {

    public static WalletId create() {
        return new WalletId(randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
