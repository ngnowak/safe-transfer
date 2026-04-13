package com.nn.safetransfer.transfer.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferResultMapperTest {

    private final TransferResultMapper mapper = new TransferResultMapper(new TransferResponseMapper());

    @Test
    void shouldReturnCreatedWhenTransferIsNewlyCreated() {
        var transfer = Transfer.completed(
                TenantId.create(),
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("10.00"),
                CurrencyCode.EUR,
                "idem",
                "ref"
        );

        var response = mapper.toTransferResponse(Result.success(transfer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldReturnOkWhenTransferAlreadyExisted() {
        var tenantId = TenantId.create();
        var sourceWallet = Wallet.create(tenantId, CustomerId.create(), CurrencyCode.EUR);
        var destinationWallet = Wallet.create(tenantId, CustomerId.create(), CurrencyCode.EUR);
        var transfer = Transfer.builder()
                .id(TransferId.newId())
                .tenantId(tenantId)
                .sourceWalletId(sourceWallet.getId())
                .destinationWalletId(destinationWallet.getId())
                .amount(new BigDecimal("10.00"))
                .currency(CurrencyCode.EUR)
                .status(TransferStatus.COMPLETED)
                .idempotencyKey("idem")
                .reference("ref")
                .createdAt(Instant.now())
                .build();

        var response = mapper.toTransferResponse(Result.success(transfer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldMapWalletNotFoundFailureToNotFound() {
        var walletId = WalletId.create();
        var tenantId = TenantId.create();

        assertThatThrownBy(() -> mapper.toTransferResponse(
                Result.failure(new TransferError.WalletNotFound(walletId, tenantId))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
