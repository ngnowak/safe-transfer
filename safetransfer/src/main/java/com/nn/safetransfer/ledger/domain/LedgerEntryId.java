package com.nn.safetransfer.ledger.domain;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public record LedgerEntryId(UUID value) {

    public static LedgerEntryId newId() {
        return new LedgerEntryId(randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
