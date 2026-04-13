package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.application.WalletError;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.NOT_FOUND;

public class WalletResultMapperTest {

    private final WalletResponseMapper walletResponseMapper = new WalletResponseMapper();
    private final WalletResultMapper walletResultMapper = new WalletResultMapper(walletResponseMapper);

    @Test
    void shouldMapSuccessResultToWalletResponse() {
        var wallet = Wallet.create(TenantId.create(), CustomerId.create(), CurrencyCode.EUR);

        WalletResponse response = walletResultMapper.toWalletResponse(Result.success(wallet));

        assertThat(response.walletId()).isEqualTo(wallet.getId().value().toString());
        assertThat(response.tenantId()).isEqualTo(wallet.getTenantId().value().toString());
        assertThat(response.currency()).isEqualTo(wallet.getCurrency().name());
        assertThat(response.status()).isEqualTo(wallet.getStatus().name());
    }

    @Test
    void shouldThrowNotFoundForWalletNotFoundFailure() {
        var walletId = WalletId.create();
        var tenantId = TenantId.create();

        assertThatThrownBy(() -> walletResultMapper.toWalletResponse(
                Result.failure(new WalletError.WalletNotFound(walletId, tenantId))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }
}
