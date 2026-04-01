package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.wallet.application.BalanceResult;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class BalanceResponseMapperTest {

    private final BalanceResponseMapper mapper = new BalanceResponseMapper();

    @Test
    void shouldMapBalanceResultToBalanceResponse() {
        // given
        var tenantId = TenantId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var balanceResult = new BalanceResult(wallet, new BigDecimal("350.50"));

        // when
        var response = mapper.toBalanceResponse(balanceResult);

        // then
        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(wallet.getId().toString()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo("EUR"),
                () -> assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("350.50"))
        );
    }
}
