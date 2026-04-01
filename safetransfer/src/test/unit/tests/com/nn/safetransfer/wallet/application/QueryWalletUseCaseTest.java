package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueryWalletUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private QueryWalletUseCase queryWalletUseCase;

    @Test
    void shouldReturnWalletWhenFound() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), CurrencyCode.EUR);
        var query = new GetWalletQuery(tenantId, walletId);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));

        // when
        var result = queryWalletUseCase.handle(query);

        // then
        assertThat(result).isEqualTo(wallet);
    }

    @Test
    void shouldThrowWhenWalletNotFound() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var query = new GetWalletQuery(tenantId, walletId);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> queryWalletUseCase.handle(query))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(walletId.value().toString())
                .hasMessageContaining(tenantId.value().toString());
    }
}
