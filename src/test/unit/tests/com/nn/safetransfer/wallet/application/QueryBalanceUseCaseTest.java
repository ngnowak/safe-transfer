package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.ledger.domain.LedgerEntryRepository;
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

import java.math.BigDecimal;
import java.util.Optional;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueryBalanceUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private QueryBalanceService queryBalanceUseCase;

    @Test
    void shouldReturnBalanceWhenWalletExists() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var wallet = Wallet.create(tenantId, CustomerId.create(), EUR);
        var query = GetBalanceQuery.builder()
                .tenantId(tenantId)
                .walletId(walletId)
                .build();

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.of(wallet));
        given(ledgerEntryRepository.calculateBalance(tenantId, walletId))
                .willReturn(new BigDecimal("500.00"));

        // when
        var result = queryBalanceUseCase.handle(query);

        // then
        assertAll(
                () -> assertThat(result.isSuccess()).isTrue(),
                () -> assertThat(result.getValue()).contains(new BalanceResult(wallet, new BigDecimal("500.00")))
        );
    }

    @Test
    void shouldReturnFailureWhenWalletDoesNotExist() {
        // given
        var tenantId = TenantId.create();
        var walletId = WalletId.create();
        var query = GetBalanceQuery.builder()
                .tenantId(tenantId)
                .walletId(walletId)
                .build();

        given(walletRepository.findByIdAndTenantId(walletId, tenantId))
                .willReturn(Optional.empty());

        // when
        var result = queryBalanceUseCase.handle(query);

        // then
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains(new WalletError.WalletNotFound(walletId, tenantId));
    }
}
