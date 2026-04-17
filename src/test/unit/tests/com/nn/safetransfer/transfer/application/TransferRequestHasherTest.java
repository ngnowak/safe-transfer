package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.FIFTY;
import static com.nn.safetransfer.TestAmounts.TWENTY_FIVE;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestHasherTest {

    private final TransferRequestHasher hasher = new TransferRequestHasher();

    @Test
    void shouldReturnSameHashForEquivalentAmountScale() {
        var sourceWalletId = UUID.randomUUID();
        var destinationWalletId = UUID.randomUUID();
        var ref = "reference";
        var request = new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                TWENTY_FIVE,
                EUR.name(),
                ref
        );

        var firstHash = hasher.hash(request);
        var secondHash = hasher.hash(request);

        assertThat(secondHash).isEqualTo(firstHash);
    }

    @Test
    void shouldReturnDifferentHashForDifferentRequestBody() {
        var sourceWalletId = UUID.randomUUID();
        var destinationWalletId = UUID.randomUUID();
        var ref = "reference";

        var firstHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                TWENTY_FIVE,
                EUR.name(),
                ref
        ));
        var secondHash = hasher.hash(new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                FIFTY,
                EUR.name(),
                ref
        ));

        assertThat(secondHash).isNotEqualTo(firstHash);
    }
}
