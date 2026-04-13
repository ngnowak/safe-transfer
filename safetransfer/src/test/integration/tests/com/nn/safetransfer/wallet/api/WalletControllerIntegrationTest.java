package com.nn.safetransfer.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebSliceTest
class WalletControllerIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String WALLET_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}";
    private static final String BALANCE_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/balance";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/{tenantId}/transfers";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private SpringDataTransferRepository transferRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldCreateWalletSuccessfully() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var request = new CreateWalletRequest(customerId, "EUR");

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isNotNull(),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.customerId()).isEqualTo(customerId.toString()),
                () -> assertThat(response.currency()).isEqualTo("EUR"),
                () -> assertThat(response.status()).isEqualTo("ACTIVE")
        );

        // verify database state
        var wallets = walletRepository.findAll();
        assertThat(wallets).hasSize(1);

        var walletJpa = wallets.getFirst();
        assertAll(
                () -> assertThat(walletJpa.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(walletJpa.getCustomerId()).isEqualTo(customerId),
                () -> assertThat(walletJpa.getCurrency()).isEqualTo("EUR"),
                () -> assertThat(walletJpa.getStatus()).isEqualTo("ACTIVE")
        );
    }

    @Test
    void shouldReturnErrorWhenCreatingDuplicateWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var currency = "EUR";
        var request = new CreateWalletRequest(customerId, currency);
        var expectedErrorMsg = "Wallet already exists for tenant %s, customer %s, and currency %s".formatted(tenantId, customerId, currency);

        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnValidationErrorWhenCurrencyIsBlank() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = new CreateWalletRequest(UUID.randomUUID(), "");

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isNotBlank(),
                () -> assertThat(error.errors()).contains("currency: must not be blank")
        );
    }

    @Test
    void shouldReturnValidationErrorWhenCustomerIdIsNull() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var body = """
                {"customerId": null, "currency": "EUR"}
                """;

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isNotBlank(),
                () -> assertThat(error.errors()).contains("customerId: must not be null")
        );
    }

    @Test
    void shouldReturnErrorWhenCurrencyCodeIsInvalid() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = new CreateWalletRequest(UUID.randomUUID(), "INVALID");

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo("No currency code for code: `INVALID`"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldGetWalletById() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var createResult = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(UUID.randomUUID(), "USD")
                        )))
                .andExpect(status().isOk())
                .andReturn();

        var createdWallet = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), WalletResponse.class
        );

        // when
        var result = mockMvc.perform(get(WALLET_PATH, tenantId, createdWallet.walletId()))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(createdWallet.walletId()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo("USD"),
                () -> assertThat(response.status()).isEqualTo("ACTIVE")
        );
    }

    @Test
    void shouldReturnNotFoundWhenWalletDoesNotExist() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();

        // when
        var result = mockMvc.perform(get(WALLET_PATH, tenantId, walletId))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("was not found"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnNotFoundWhenWalletBelongsToDifferentTenant() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var otherTenantId = UUID.randomUUID();
        var createResult = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(UUID.randomUUID(), "EUR")
                        )))
                .andExpect(status().isOk())
                .andReturn();

        var createdWallet = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), WalletResponse.class
        );

        // when / then
        mockMvc.perform(get(WALLET_PATH, otherTenantId, createdWallet.walletId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDepositFundsSuccessfully() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "PLN");
        var depositRequest = new DepositRequest(new BigDecimal("500.00"), "PLN", "Initial deposit");

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), DepositResponse.class
        );

        assertAll(
                () -> assertThat(response.ledgerEntryId()).isNotNull(),
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("500.00")),
                () -> assertThat(response.currency()).isEqualTo("PLN"),
                () -> assertThat(response.entryType()).isEqualTo("CREDIT"),
                () -> assertThat(response.reference()).isEqualTo("Initial deposit"),
                () -> assertThat(response.createdAt()).isNotNull()
        );

        // verify database state
        var ledgerEntries = ledgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(1);

        var entry = ledgerEntries.getFirst();
        assertAll(
                () -> assertThat(entry.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entry.getWalletId()).isEqualTo(UUID.fromString(walletId)),
                () -> assertThat(entry.getType()).isEqualTo("CREDIT"),
                () -> assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"))
        );
    }

    @Test
    void shouldDepositMultipleTimesAndAccumulateBalance() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");

        // when
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(new BigDecimal("100.00"), "EUR", "First")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(new BigDecimal("250.50"), "EUR", "Second")
                        )))
                .andExpect(status().isOk());

        // then
        var balance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(walletId));
        assertThat(balance).isEqualByComparingTo(new BigDecimal("350.50"));
    }

    @Test
    void shouldReturnNotFoundWhenDepositingToNonExistentWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();
        var depositRequest = new DepositRequest(new BigDecimal("100.00"), "EUR", null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("was not found"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenDepositCurrencyMismatch() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");
        var depositRequest = new DepositRequest(new BigDecimal("100.00"), "USD", null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("Wallet currency is"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnValidationErrorWhenDepositAmountIsZero() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");
        var depositRequest = new DepositRequest(new BigDecimal("0.00"), "EUR", null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isNotBlank(),
                () -> assertThat(error.errors()).contains("amount: must be greater than or equal to 0.01")
        );
    }

    @Test
    void shouldAllowDifferentCurrencyWalletsForSameCustomer() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        // when / then - create EUR wallet
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(customerId, "EUR")
                        )))
                .andExpect(status().isOk());

        // create PLN wallet for same customer
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(customerId, "PLN")
                        )))
                .andExpect(status().isOk());

        assertThat(walletRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldGetBalanceSuccessfully() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");
        deposit(tenantId, walletId, "500.00", "EUR");

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo("EUR"),
                () -> assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("500.00"))
        );
    }

    @Test
    void shouldGetZeroBalanceForNewWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO)
        );
    }

    @Test
    void shouldReturnNotFoundWhenGettingBalanceForNonExistentWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = UUID.randomUUID();

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("was not found"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReflectBalanceAfterTransfer() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var transferRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("300.00"))
                .currency("EUR")
                .reference("Test transfer")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated());

        // when
        var sourceResult = mockMvc.perform(get(BALANCE_PATH, tenantId, sourceWalletId))
                .andExpect(status().isOk())
                .andReturn();

        var destinationResult = mockMvc.perform(get(BALANCE_PATH, tenantId, destinationWalletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var sourceBalance = objectMapper.readValue(
                sourceResult.getResponse().getContentAsString(), BalanceResponse.class
        );
        var destinationBalance = objectMapper.readValue(
                destinationResult.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(sourceBalance.balance()).isEqualByComparingTo(new BigDecimal("700.00")),
                () -> assertThat(destinationBalance.balance()).isEqualByComparingTo(new BigDecimal("300.00"))
        );
    }

    private String createWallet(UUID tenantId, String currency) throws Exception {
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(UUID.randomUUID(), currency)
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        ).walletId();
    }

    private void deposit(UUID tenantId, String walletId, String amount, String currency) throws Exception {
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(new BigDecimal(amount), currency, null)
                        )))
                .andExpect(status().isOk());
    }

    private ErrorDto readError(String responseBody) throws Exception {
        return objectMapper.readValue(responseBody, ErrorDto.class);
    }
}
