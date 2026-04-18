package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;

class TransferRiskPolicyTest {

    @Test
    void shouldAllowTransferWhenPolicyIsDisabled() {
        // given
        var policy = new TransferRiskPolicy(new TransferRiskProperties(false, new BigDecimal("100.00")));
        var request = requestWithAmount("1000.00");

        // when
        var result = policy.validate(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldAllowTransferAtConfiguredLimit() {
        // given
        var policy = new TransferRiskPolicy(new TransferRiskProperties(true, new BigDecimal("100.00")));
        var request = requestWithAmount("100.00");

        // when
        var result = policy.validate(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectTransferAboveConfiguredLimit() {
        // given
        var policy = new TransferRiskPolicy(new TransferRiskProperties(true, new BigDecimal("100.00")));
        var request = requestWithAmount("100.01");

        // when
        var result = policy.validate(request);

        // then
        assertThat(result).containsInstanceOf(TransferError.TransferLimitExceeded.class);
        assertThat(result.orElseThrow().getMessage()).contains("100.01").contains("100.00");
    }

    private CreateTransferRequest requestWithAmount(String amount) {
        return CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal(amount))
                .currency(EUR.name())
                .reference("Risk policy test")
                .build();
    }
}
