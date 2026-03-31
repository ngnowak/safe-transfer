package com.nn.safetransfer.wallet.domain;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public record TenantId(UUID value) {

    public static TenantId create() {
        return new TenantId(randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
