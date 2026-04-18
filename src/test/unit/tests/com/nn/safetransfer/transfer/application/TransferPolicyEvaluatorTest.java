package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;

class TransferPolicyEvaluatorTest {

    @Test
    void shouldPassWhenAllPoliciesPass() {
        // given
        var evaluator = new TransferPolicyEvaluator(List.of(
                _ -> Result.emptySuccessResult(),
                _ -> Result.emptySuccessResult()
        ));

        // when
        var result = evaluator.validate(request());

        // then
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldReturnFirstPolicyViolation() {
        // given
        var firstViolation = new TransferError.TransferLimitExceeded(
                new BigDecimal("200.00"),
                new BigDecimal("100.00")
        );
        var secondViolation = new TransferError.SameWalletTransfer();
        var evaluator = new TransferPolicyEvaluator(List.of(
                _ -> Result.emptySuccessResult(),
                _ -> Result.failure(firstViolation),
                _ -> Result.failure(secondViolation)
        ));

        // when
        var result = evaluator.validate(request());

        // then
        assertThat(result.getError()).contains(firstViolation);
    }

    private CreateTransferRequest request() {
        return CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency(EUR.name())
                .reference("Policy evaluator test")
                .build();
    }
}
