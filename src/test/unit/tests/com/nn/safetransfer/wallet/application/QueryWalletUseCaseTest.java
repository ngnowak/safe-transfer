package com.nn.safetransfer.wallet.application;

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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueryWalletUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private QueryWalletService queryWalletUseCase;

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
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).contains(wallet);
    }

    @Test
    void shouldReturnFailureWhenWalletNotFound() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var query = new GetWalletQuery(tenantId, walletId);

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.empty());

        // when / then
        var result = queryWalletUseCase.handle(query);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains(new WalletError.WalletNotFound(walletId, tenantId));
    }
}
