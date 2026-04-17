package com.nn.safetransfer.e2e.client;

import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class WalletApiClient {

    private static final String WALLETS_PATH = "/api/v1/tenants/%s/wallets";
    private static final String WALLET_BY_ID_PATH = WALLETS_PATH + "/%s";
    private static final String WALLET_BALANCE_PATH = WALLET_BY_ID_PATH + "/balance";
    private static final String WALLET_DEPOSITS_PATH = WALLET_BY_ID_PATH + "/deposits";

    private final E2eHttpClient httpClient;

    public WalletApiClient(E2eHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public WalletResponse createWallet(UUID tenantId, UUID customerId, String currency) throws IOException, InterruptedException {
        return httpClient.post(
                WALLETS_PATH.formatted(tenantId),
                new CreateWalletRequest(customerId, currency),
                Map.of(),
                HttpStatus.OK.value(),
                WalletResponse.class
        );
    }

    public ErrorDto createWalletError(UUID tenantId, UUID customerId, String currency) throws IOException, InterruptedException {
        return httpClient.post(
                WALLETS_PATH.formatted(tenantId),
                new CreateWalletRequest(customerId, currency),
                Map.of(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorDto.class
        );
    }

    public WalletResponse getWallet(UUID tenantId, UUID walletId) throws IOException, InterruptedException {
        return httpClient.get(WALLET_BY_ID_PATH.formatted(tenantId, walletId), HttpStatus.OK.value(), WalletResponse.class);
    }

    public ErrorDto getWalletNotFound(UUID tenantId, UUID walletId) throws IOException, InterruptedException {
        return httpClient.get(WALLET_BY_ID_PATH.formatted(tenantId, walletId), HttpStatus.NOT_FOUND.value(), ErrorDto.class);
    }

    public BalanceResponse getBalance(UUID tenantId, UUID walletId) throws IOException, InterruptedException {
        return httpClient.get(WALLET_BALANCE_PATH.formatted(tenantId, walletId), HttpStatus.OK.value(), BalanceResponse.class);
    }

    public DepositResponse deposit(
            UUID tenantId,
            UUID walletId,
            BigDecimal amount,
            String currency,
            String reference
    ) throws IOException, InterruptedException {
        return httpClient.post(
                WALLET_DEPOSITS_PATH.formatted(tenantId, walletId),
                new DepositRequest(amount, currency, reference),
                Map.of(),
                HttpStatus.OK.value(),
                DepositResponse.class
        );
    }

    public ErrorDto depositBadRequest(
            UUID tenantId,
            UUID walletId,
            BigDecimal amount,
            String currency,
            String reference
    ) throws IOException, InterruptedException {
        return httpClient.post(
                WALLET_DEPOSITS_PATH.formatted(tenantId, walletId),
                new DepositRequest(amount, currency, reference),
                Map.of(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorDto.class
        );
    }
}
