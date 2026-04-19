package com.nn.safetransfer.wallet.application;

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

import org.springframework.dao.DataIntegrityViolationException;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
                () -> assertThat(result.isSuccess()).isTrue(),
                () -> assertThat(result.getValue()).hasValue(savedWallet)
        );
    }

    @Test
    void shouldReturnDuplicateWalletFailureWhenConstraintViolated() {
        // given - simulates concurrent creation: DB unique constraint fires
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var command = new CreateWalletCommand(tenantId, customerId, EUR);
        var expectedError = new WalletError.DuplicateWallet(tenantId, customerId, EUR);

        given(walletRepository.save(any(Wallet.class)))
                .willThrow(new DataIntegrityViolationException("uk_wallet_tenant_customer_currency"));

        // when
        var result = walletApplicationService.handle(command);

        // then
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).hasValue(expectedError);
        verify(walletRepository).save(any());
    }
}
