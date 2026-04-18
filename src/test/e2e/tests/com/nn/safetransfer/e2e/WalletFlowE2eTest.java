package com.nn.safetransfer.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.e2e.client.E2eHttpClient;
import com.nn.safetransfer.e2e.client.TransferApiClient;
import com.nn.safetransfer.e2e.client.WalletApiClient;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.WalletStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_THOUSAND;
import static com.nn.safetransfer.TestAmounts.TWENTY_FIVE;
import static com.nn.safetransfer.TestAmounts.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

class WalletFlowE2eTest {

    private static final String BASE_URL = System.getenv().getOrDefault("SAFETRANSFER_BASE_URL", "http://localhost:8080");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final E2eHttpClient HTTP_CLIENT = new E2eHttpClient(BASE_URL, OBJECT_MAPPER);
    private static final WalletApiClient WALLET_API_CLIENT = new WalletApiClient(HTTP_CLIENT);
    private static final TransferApiClient TRANSFER_API_CLIENT = new TransferApiClient(HTTP_CLIENT);
    public static final String HEALTH_PATH = "/actuator/health";

    @BeforeAll
    static void shouldReachApplication() throws Exception {
        var response = HTTP_CLIENT.get(HEALTH_PATH);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void shouldCreateWalletAndGetItById() throws Exception {
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        var createdWallet = WALLET_API_CLIENT.createWallet(tenantId, customerId, CurrencyCode.EUR.name());
        var wallet = WALLET_API_CLIENT.getWallet(tenantId, UUID.fromString(createdWallet.walletId()));

        assertThat(wallet.walletId()).isEqualTo(createdWallet.walletId());
        assertThat(wallet.tenantId()).isEqualTo(tenantId.toString());
        assertThat(wallet.customerId()).isEqualTo(customerId.toString());
        assertThat(wallet.currency()).isEqualTo(CurrencyCode.EUR.name());
        assertThat(wallet.status()).isEqualTo(WalletStatus.ACTIVE.name());
    }

    @Test
    void shouldRejectDuplicateWalletForSameCustomerTenantAndCurrency() throws Exception {
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        WALLET_API_CLIENT.createWallet(tenantId, customerId, CurrencyCode.EUR.name());
        var error = WALLET_API_CLIENT.createWalletError(tenantId, customerId, CurrencyCode.EUR.name());

        assertThat(error.errorMessage()).isEqualTo(
                "Wallet already exists for tenant %s, customer %s, and currency %s"
                        .formatted(tenantId, customerId, CurrencyCode.EUR)
        );
    }

    @Test
    void shouldRejectInvalidWalletCurrency() throws Exception {
        var error = WALLET_API_CLIENT.createWalletError(UUID.randomUUID(), UUID.randomUUID(), "INVALID");

        assertThat(error.errorMessage()).isEqualTo("No currency code for code: `INVALID`");
    }

    @Test
    void shouldRejectTransferWhenSourceWalletHasInsufficientFunds() throws Exception {
        var tenantId = UUID.randomUUID();
        var sourceWallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());
        var destinationWallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());
        WALLET_API_CLIENT.deposit(
                tenantId,
                UUID.fromString(sourceWallet.walletId()),
                ONE_HUNDRED,
                CurrencyCode.EUR.name(),
                "e2e insufficient funds deposit"
        );

        var error = TRANSFER_API_CLIENT.createTransferError(
                tenantId,
                CreateTransferRequest.builder()
                        .sourceWalletId(UUID.fromString(sourceWallet.walletId()))
                        .destinationWalletId(UUID.fromString(destinationWallet.walletId()))
                        .amount(ONE_THOUSAND)
                        .currency(CurrencyCode.EUR.name())
                        .reference("e2e insufficient funds transfer")
                        .build(),
                UUID.randomUUID().toString()
        );

        assertThat(error.errorMessage()).isEqualTo(
                "Wallet '%s' has insufficient funds. Available: %s, requested: %s"
                        .formatted(sourceWallet.walletId(), ONE_HUNDRED, ONE_THOUSAND)
        );
        assertBalance(tenantId, sourceWallet.walletId(), ONE_HUNDRED);
        assertBalance(tenantId, destinationWallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldRejectZeroDepositAmount() throws Exception {
        var tenantId = UUID.randomUUID();
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());

        var error = WALLET_API_CLIENT.depositBadRequest(
                tenantId,
                UUID.fromString(wallet.walletId()),
                ZERO,
                CurrencyCode.EUR.name(),
                "e2e zero deposit"
        );

        assertThat(error.errors())
                .anySatisfy(message -> assertThat(message).isEqualTo("amount: must be greater than or equal to 0.01"));
        assertBalance(tenantId, wallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldRejectDepositCurrencyMismatch() throws Exception {
        var tenantId = UUID.randomUUID();
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());

        var error = WALLET_API_CLIENT.depositBadRequest(
                tenantId,
                UUID.fromString(wallet.walletId()),
                TWENTY_FIVE,
                CurrencyCode.USD.name(),
                "e2e currency mismatch deposit"
        );

        assertThat(error.errorMessage()).isEqualTo(
                "Wallet currency is '%s' but request currency is '%s'"
                        .formatted(CurrencyCode.EUR, CurrencyCode.USD)
        );
        assertBalance(tenantId, wallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldReturnZeroBalanceForNewWallet() throws Exception {
        var tenantId = UUID.randomUUID();
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());

        assertBalance(tenantId, wallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldKeepWalletsTenantScoped() throws Exception {
        var tenantId = UUID.randomUUID();
        var otherTenantId = UUID.randomUUID();
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());

        var error = WALLET_API_CLIENT.getWalletNotFound(otherTenantId, UUID.fromString(wallet.walletId()));

        assertThat(error.errorMessage()).isEqualTo(
                "Wallet with id '%s' was not found for tenant '%s'"
                        .formatted(wallet.walletId(), otherTenantId)
        );
        var walletForOwningTenant = WALLET_API_CLIENT.getWallet(tenantId, UUID.fromString(wallet.walletId()));
        assertThat(walletForOwningTenant.walletId()).isEqualTo(wallet.walletId());
    }

    @Test
    void shouldDepositMoneyToWalletAndUpdateBalance() throws Exception {
        var tenantId = UUID.randomUUID();
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR.name());

        var deposit = WALLET_API_CLIENT.deposit(
                tenantId,
                UUID.fromString(wallet.walletId()),
                TWENTY_FIVE,
                CurrencyCode.EUR.name(),
                "e2e wallet deposit"
        );

        assertThat(deposit.ledgerEntryId()).isNotBlank();
        assertThat(deposit.walletId()).isEqualTo(wallet.walletId());
        assertThat(deposit.amount()).isEqualByComparingTo(TWENTY_FIVE);
        assertThat(deposit.currency()).isEqualTo(CurrencyCode.EUR.name());
        assertThat(deposit.reference()).isEqualTo("e2e wallet deposit");
        assertBalance(tenantId, wallet.walletId(), TWENTY_FIVE);
    }

    private void assertBalance(UUID tenantId, String walletId, BigDecimal expectedBalance) throws Exception {
        var balance = WALLET_API_CLIENT.getBalance(tenantId, UUID.fromString(walletId));

        assertThat(balance.walletId()).isEqualTo(walletId);
        assertThat(balance.tenantId()).isEqualTo(tenantId.toString());
        assertThat(balance.currency()).isEqualTo(CurrencyCode.EUR.name());
        assertThat(balance.balance()).isEqualByComparingTo(expectedBalance);
    }
}
