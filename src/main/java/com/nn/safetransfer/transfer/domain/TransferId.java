package com.nn.safetransfer.transfer.domain;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public record TransferId(UUID value) {

    public static TransferId newId() {
        return new TransferId(randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
