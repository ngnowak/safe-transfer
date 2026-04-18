package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.GBP;
import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class WalletResponseMapperTest {

    private final WalletResponseMapper mapper = new WalletResponseMapper();

    @Test
    void shouldMapWalletToWalletResponse() {
        // given
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var wallet = Wallet.create(tenantId, customerId, GBP);

        // when
        var response = mapper.toWalletResponse(wallet);

        // then
        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(wallet.getId().value().toString()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.value().toString()),
                () -> assertThat(response.customerId()).isEqualTo(customerId.value().toString()),
                () -> assertThat(response.currency()).isEqualTo(wallet.getCurrency().name()),
                () -> assertThat(response.status()).isEqualTo(ACTIVE.name())
        );
    }
}
