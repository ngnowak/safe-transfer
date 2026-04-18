package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.wallet.application.BalanceResult;
import com.nn.safetransfer.wallet.application.WalletError;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class BalanceResponseMapperTest {

    private final BalanceResponseMapper mapper = new BalanceResponseMapper();

    @Test
    void shouldMapBalanceResultToBalanceResponse() {
        // given
        var tenantId = TenantId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var balanceResult = new BalanceResult(wallet, new BigDecimal("350.50"));

        // when
        var response = mapper.toBalanceResponse(Result.success(balanceResult));

        // then
        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(wallet.getId().toString()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo(wallet.getCurrency().name()),
                () -> assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("350.50"))
        );
    }

    @Test
    void shouldThrowNotFoundWhenBalanceQueryFailsBecauseWalletIsMissing() {
        var walletId = WalletId.create();
        var tenantId = TenantId.create();

        assertThatThrownBy(() -> mapper.toBalanceResponse(Result.failure(new WalletError.WalletNotFound(walletId, tenantId))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }
}
