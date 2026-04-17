package com.nn.safetransfer.wallet.domain;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public record CustomerId(UUID value) {

    public static CustomerId create() {
        return new CustomerId(randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
