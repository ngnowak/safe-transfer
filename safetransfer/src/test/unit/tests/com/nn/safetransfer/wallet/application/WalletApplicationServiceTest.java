package com.nn.safetransfer.wallet.application;

import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletApplicationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletApplicationService walletApplicationService;

    @Test
    void shouldCreateWalletSuccessfully() {
        // given
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var command = new CreateWalletCommand(tenantId, customerId, EUR);
        var captor = ArgumentCaptor.forClass(Wallet.class);

        given(walletRepository.existsByTenantIdAndCustomerIdAndCurrency(tenantId, customerId, EUR))
                .willReturn(false);
        given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        var result = walletApplicationService.handle(command);

        // then
        verify(walletRepository).save(captor.capture());
        var savedWallet = captor.getValue();

        assertAll(
                () -> assertThat(savedWallet.getId()).isNotNull(),
                () -> assertThat(savedWallet.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(savedWallet.getCustomerId()).isEqualTo(customerId),
                () -> assertThat(savedWallet.getCurrency()).isEqualTo(EUR),
                () -> assertThat(savedWallet.getStatus()).isEqualTo(ACTIVE),
                () -> assertThat(result).isEqualTo(savedWallet)
        );
    }

    @Test
    void shouldThrowWhenWalletAlreadyExists() {
        // given
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var command = new CreateWalletCommand(tenantId, customerId, EUR);

        given(walletRepository.existsByTenantIdAndCustomerIdAndCurrency(tenantId, customerId, EUR))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> walletApplicationService.handle(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Wallet already exists for this tenant, customer, and currency");

        verify(walletRepository, never()).save(any());
    }
}
