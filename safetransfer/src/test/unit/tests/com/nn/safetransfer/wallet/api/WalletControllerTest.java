package com.nn.safetransfer.wallet.api;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.api.mapper.BalanceResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.DepositResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.WalletResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.WalletResultMapper;
import com.nn.safetransfer.wallet.application.BalanceResult;
import com.nn.safetransfer.wallet.application.CreateWalletCommand;
import com.nn.safetransfer.wallet.application.CreateWalletUseCase;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.GetBalanceQuery;
import com.nn.safetransfer.wallet.application.GetWalletQuery;
import com.nn.safetransfer.wallet.application.QueryBalanceUseCase;
import com.nn.safetransfer.wallet.application.QueryWalletUseCase;
import com.nn.safetransfer.wallet.application.WalletError;
import com.nn.safetransfer.wallet.application.mapper.CreateWalletCommandMapper;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.nn.safetransfer.common.domain.result.Result.success;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private CreateWalletUseCase createWalletUseCase;

    @Mock
    private WalletResponseMapper walletResponseMapper;

    @Mock
    private WalletResultMapper walletResultMapper;

    @Mock
    private CreateWalletCommandMapper createWalletCommandMapper;

    @Mock
    private QueryWalletUseCase queryWalletUseCase;

    @Mock
    private QueryBalanceUseCase queryBalanceUseCase;

    @Mock
    private DepositService depositService;

    @Mock
    private DepositResponseMapper depositResponseMapper;

    @Mock
    private BalanceResponseMapper balanceResponseMapper;

    @InjectMocks
    private WalletController walletController;

    @Test
    void shouldCreateWallet() {
        // given
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var request = new CreateWalletRequest(customerId, "EUR");
        var command = new CreateWalletCommand(new TenantId(tenantId), new CustomerId(customerId), EUR);
        var wallet = Wallet.create(new TenantId(tenantId), new CustomerId(customerId), EUR);
        var expectedResponse = WalletResponse.builder()
                .walletId(wallet.getId().toString())
                .tenantId(tenantId.toString())
                .customerId(customerId.toString())
                .currency("EUR")
                .status("ACTIVE")
                .build();

        Result<WalletError, Wallet> result = success(wallet);
        given(createWalletCommandMapper.toCreateWalletCommand(tenantId, request)).willReturn(command);
        given(createWalletUseCase.handle(command)).willReturn(result);
        given(walletResultMapper.toWalletResponse(result)).willReturn(expectedResponse);

        // when
        var response = walletController.createWallet(tenantId, request);

        // then
        assertAll(
                () -> assertThat(response).isEqualTo(expectedResponse),
                () -> verify(createWalletCommandMapper).toCreateWalletCommand(tenantId, request),
                () -> verify(createWalletUseCase).handle(command),
                () -> verify(walletResultMapper).toWalletResponse(result)
        );
    }

    @Test
    void shouldGetWallet() {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();
        var wallet = Wallet.create(new TenantId(tenantId), CustomerId.create(), EUR);
        var expectedResponse = WalletResponse.builder()
                .walletId(walletId.toString())
                .tenantId(tenantId.toString())
                .currency("EUR")
                .status("ACTIVE")
                .build();

        given(queryWalletUseCase.handle(any(GetWalletQuery.class))).willReturn(wallet);
        given(walletResponseMapper.toWalletResponse(wallet)).willReturn(expectedResponse);

        // when
        var response = walletController.getWallet(tenantId, walletId);

        // then
        assertAll(
                () -> assertThat(response).isEqualTo(expectedResponse),
                () -> verify(queryWalletUseCase).handle(any(GetWalletQuery.class)),
                () -> verify(walletResponseMapper).toWalletResponse(wallet)
        );
    }

    @Test
    void shouldDeposit() {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();
        var request = new DepositRequest(new BigDecimal("100.00"), "EUR", "Ref");
        var ledgerEntry = LedgerEntry.credit(
                new TenantId(tenantId), new WalletId(walletId),
                new BigDecimal("100.00"), EUR, "Ref"
        );
        var expectedResponse = DepositResponse.builder()
                .ledgerEntryId(ledgerEntry.getId().toString())
                .walletId(walletId.toString())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .entryType("CREDIT")
                .reference("Ref")
                .createdAt(Instant.now())
                .build();

        given(depositService.deposit(eq(new TenantId(tenantId)), eq(new WalletId(walletId)), eq(request)))
                .willReturn(ledgerEntry);
        given(depositResponseMapper.toDepositResponse(ledgerEntry)).willReturn(expectedResponse);

        // when
        var response = walletController.deposit(tenantId, walletId, request);

        // then
        assertAll(
                () -> assertThat(response).isEqualTo(expectedResponse),
                () -> verify(depositService).deposit(new TenantId(tenantId), new WalletId(walletId), request),
                () -> verify(depositResponseMapper).toDepositResponse(ledgerEntry)
        );
    }

    @Test
    void shouldGetBalance() {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();
        var wallet = Wallet.create(new TenantId(tenantId), CustomerId.create(), EUR);
        var balanceResult = new BalanceResult(wallet, new BigDecimal("500.00"));
        var expectedResponse = BalanceResponse.builder()
                .walletId(wallet.getId().toString())
                .tenantId(tenantId.toString())
                .currency("EUR")
                .balance(new BigDecimal("500.00"))
                .build();

        given(queryBalanceUseCase.handle(any(GetBalanceQuery.class))).willReturn(balanceResult);
        given(balanceResponseMapper.toBalanceResponse(balanceResult)).willReturn(expectedResponse);

        // when
        var response = walletController.getBalance(tenantId, walletId);

        // then
        assertAll(
                () -> assertThat(response).isEqualTo(expectedResponse),
                () -> verify(queryBalanceUseCase).handle(any(GetBalanceQuery.class)),
                () -> verify(balanceResponseMapper).toBalanceResponse(balanceResult)
        );
    }
}
