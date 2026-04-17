package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestHasherTest {

    private final TransferRequestHasher hasher = new TransferRequestHasher();

    @Test
    void shouldReturnSameHashForEquivalentAmountScale() {
        var sourceWalletId = UUID.randomUUID();
        var destinationWalletId = UUID.randomUUID();

        var firstHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("25.00"),
                "EUR",
                "reference"
        ));
        var secondHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("25.0"),
                "EUR",
                "reference"
        ));

        assertThat(secondHash).isEqualTo(firstHash);
    }

    @Test
    void shouldReturnDifferentHashForDifferentRequestBody() {
        var sourceWalletId = UUID.randomUUID();
        var destinationWalletId = UUID.randomUUID();

        var firstHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("25.00"),
                "EUR",
                "reference"
        ));
        var secondHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("30.00"),
                "EUR",
                "reference"
        ));

        assertThat(secondHash).isNotEqualTo(firstHash);
    }
}
