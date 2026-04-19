package com.nn.safetransfer.wallet.api;

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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.FIVE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_THOUSAND;
import static com.nn.safetransfer.TestAmounts.SEVEN_HUNDRED;
import static com.nn.safetransfer.TestAmounts.THREE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.THREE_HUNDRED_FIFTY_50;
import static com.nn.safetransfer.TestAmounts.TWO_HUNDRED_FIFTY_50;
import static com.nn.safetransfer.TestAmounts.ZERO;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY;
import static com.nn.safetransfer.ledger.domain.LedgerEntryType.CREDIT;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.PLN;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.USD;
import static com.nn.safetransfer.wallet.domain.WalletStatus.ACTIVE;
import static java.util.UUID.randomUUID;
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

    @Autowired
    private JsonMapper jsonMapper;

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
        var tenantId = randomUUID();
        var customerId = randomUUID();
        var request = new CreateWalletRequest(customerId, EUR.name());

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isNotNull(),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.customerId()).isEqualTo(customerId.toString()),
                () -> assertThat(response.currency()).isEqualTo(EUR.name()),
                () -> assertThat(response.status()).isEqualTo(ACTIVE.name())
        );

        // verify database state
        var wallets = walletRepository.findAll();
        assertThat(wallets).hasSize(1);

        var walletJpa = wallets.getFirst();
        assertAll(
                () -> assertThat(walletJpa.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(walletJpa.getCustomerId()).isEqualTo(customerId),
                () -> assertThat(walletJpa.getCurrency()).isEqualTo(EUR.name()),
                () -> assertThat(walletJpa.getStatus()).isEqualTo(ACTIVE.name())
        );
    }

    @Test
    void shouldReturnErrorWhenCreatingDuplicateWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var customerId = randomUUID();
        var currency = EUR.name();
        var request = new CreateWalletRequest(customerId, currency);
        var expectedErrorMsg = "Wallet already exists for tenant %s, customer %s, and currency %s".formatted(tenantId, customerId, currency);

        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
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
        var tenantId = randomUUID();
        var request = new CreateWalletRequest(randomUUID(), "");

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = """
                Validation failed for argument [1] in public com.nn.safetransfer.wallet.api.dto.WalletResponse com.nn.safetransfer.wallet.api.WalletController.createWallet(java.util.UUID,com.nn.safetransfer.wallet.api.dto.CreateWalletRequest): [Field error in object 'createWalletRequest' on field 'currency': rejected value []; codes [NotBlank.createWalletRequest.currency,NotBlank.currency,NotBlank.java.lang.String,NotBlank]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [createWalletRequest.currency,currency]; arguments []; default message [currency]]; default message [must not be blank]]\s""";
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).contains("currency: must not be blank")
        );
    }

    @Test
    void shouldReturnValidationErrorWhenCustomerIdIsNull() throws Exception {
        // given
        var tenantId = randomUUID();
        var body = """
                {"customerId": null, "currency": "%s"}
                """.formatted(EUR.name());

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = """
                Validation failed for argument [1] in public com.nn.safetransfer.wallet.api.dto.WalletResponse com.nn.safetransfer.wallet.api.WalletController.createWallet(java.util.UUID,com.nn.safetransfer.wallet.api.dto.CreateWalletRequest): [Field error in object 'createWalletRequest' on field 'customerId': rejected value [null]; codes [NotNull.createWalletRequest.customerId,NotNull.customerId,NotNull.java.util.UUID,NotNull]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [createWalletRequest.customerId,customerId]; arguments []; default message [customerId]]; default message [must not be null]]\s""";
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).contains("customerId: must not be null")
        );
    }

    @Test
    void shouldReturnErrorWhenCurrencyCodeIsInvalid() throws Exception {
        // given
        var tenantId = randomUUID();
        var invalidCurrency = "INVALID";
        var request = new CreateWalletRequest(randomUUID(), invalidCurrency);
        var expectedErrorMsg = "No currency code for code: `%s`".formatted(invalidCurrency);

        // when
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
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
    void shouldGetWalletById() throws Exception {
        // given
        var tenantId = randomUUID();
        var currency = USD.name();
        var createResult = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(randomUUID(), currency)
                        )))
                .andExpect(status().isOk())
                .andReturn();

        var createdWallet = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(), WalletResponse.class
        );

        // when
        var result = mockMvc.perform(get(WALLET_PATH, tenantId, createdWallet.walletId()))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(createdWallet.walletId()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo(currency),
                () -> assertThat(response.status()).isEqualTo(ACTIVE.name())
        );
    }

    @Test
    void shouldReturnNotFoundWhenWalletDoesNotExist() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = randomUUID();

        // when
        var result = mockMvc.perform(get(WALLET_PATH, tenantId, walletId))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(walletId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnNotFoundWhenWalletBelongsToDifferentTenant() throws Exception {
        // given
        var tenantId = randomUUID();
        var otherTenantId = randomUUID();
        var createResult = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(randomUUID(), EUR.name())
                        )))
                .andExpect(status().isOk())
                .andReturn();

        var createdWallet = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(), WalletResponse.class
        );

        // when / then
        mockMvc.perform(get(WALLET_PATH, otherTenantId, createdWallet.walletId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDepositFundsSuccessfully() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, PLN.name());
        var reference = "Initial deposit";
        var depositRequest = new DepositRequest(FIVE_HUNDRED, PLN.name(), reference);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), DepositResponse.class
        );

        assertAll(
                () -> assertThat(response.ledgerEntryId()).isNotNull(),
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.amount()).isEqualByComparingTo(FIVE_HUNDRED),
                () -> assertThat(response.currency()).isEqualTo(PLN.name()),
                () -> assertThat(response.entryType()).isEqualTo(CREDIT.name()),
                () -> assertThat(response.reference()).isEqualTo(reference),
                () -> assertThat(response.createdAt()).isNotNull()
        );

        // verify database state
        var ledgerEntries = ledgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(1);

        var entry = ledgerEntries.getFirst();
        assertAll(
                () -> assertThat(entry.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entry.getWalletId()).isEqualTo(UUID.fromString(walletId)),
                () -> assertThat(entry.getType()).isEqualTo(CREDIT.name()),
                () -> assertThat(entry.getAmount()).isEqualByComparingTo(FIVE_HUNDRED)
        );
    }

    @Test
    void shouldDepositMultipleTimesAndAccumulateBalance() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());

        // when
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new DepositRequest(ONE_HUNDRED, EUR.name(), "First")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new DepositRequest(TWO_HUNDRED_FIFTY_50, EUR.name(), "Second")
                        )))
                .andExpect(status().isOk());

        // then
        var balance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(walletId));
        assertThat(balance).isEqualByComparingTo(THREE_HUNDRED_FIFTY_50);
    }

    @Test
    void shouldReturnNotFoundWhenDepositingToNonExistentWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = randomUUID();
        var depositRequest = new DepositRequest(ONE_HUNDRED, EUR.name(), null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(walletId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenDepositCurrencyMismatch() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());
        var depositRequest = new DepositRequest(ONE_HUNDRED, USD.name(), null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet currency is '%s' but request currency is '%s'"
                .formatted(EUR, USD);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnValidationErrorWhenDepositAmountIsZero() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());
        var depositRequest = new DepositRequest(ZERO, EUR.name(), null);

        // when
        var result = mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = """
                Validation failed for argument [2] in public com.nn.safetransfer.wallet.api.dto.DepositResponse com.nn.safetransfer.wallet.api.WalletController.deposit(java.util.UUID,java.util.UUID,com.nn.safetransfer.wallet.api.dto.DepositRequest): [Field error in object 'depositRequest' on field 'amount': rejected value [0.00]; codes [DecimalMin.depositRequest.amount,DecimalMin.amount,DecimalMin.java.math.BigDecimal,DecimalMin]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [depositRequest.amount,amount]; arguments []; default message [amount],true,0.01]; default message [must be greater than or equal to 0.01]]\s""";
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).contains("amount: must be greater than or equal to 0.01")
        );
    }

    @Test
    void shouldAllowDifferentCurrencyWalletsForSameCustomer() throws Exception {
        // given
        var tenantId = randomUUID();
        var customerId = randomUUID();

        // when / then - create EUR wallet
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(customerId, EUR.name())
                        )))
                .andExpect(status().isOk());

        // create PLN wallet for same customer
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(customerId, PLN.name())
                        )))
                .andExpect(status().isOk());

        assertThat(walletRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldGetBalanceSuccessfully() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, walletId, FIVE_HUNDRED, EUR.name());

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.currency()).isEqualTo(EUR.name()),
                () -> assertThat(response.balance()).isEqualByComparingTo(FIVE_HUNDRED)
        );
    }

    @Test
    void shouldGetZeroBalanceForNewWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(response.walletId()).isEqualTo(walletId),
                () -> assertThat(response.balance()).isEqualByComparingTo(ZERO)
        );
    }

    @Test
    void shouldReturnNotFoundWhenGettingBalanceForNonExistentWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = randomUUID();

        // when
        var result = mockMvc.perform(get(BALANCE_PATH, tenantId, walletId))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(walletId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReflectBalanceAfterTransfer() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var transferRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(THREE_HUNDRED)
                .currency(EUR.name())
                .reference("Test transfer")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated());

        // when
        var sourceResult = mockMvc.perform(get(BALANCE_PATH, tenantId, sourceWalletId))
                .andExpect(status().isOk())
                .andReturn();

        var destinationResult = mockMvc.perform(get(BALANCE_PATH, tenantId, destinationWalletId))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var sourceBalance = jsonMapper.readValue(
                sourceResult.getResponse().getContentAsString(), BalanceResponse.class
        );
        var destinationBalance = jsonMapper.readValue(
                destinationResult.getResponse().getContentAsString(), BalanceResponse.class
        );

        assertAll(
                () -> assertThat(sourceBalance.balance()).isEqualByComparingTo(SEVEN_HUNDRED),
                () -> assertThat(destinationBalance.balance()).isEqualByComparingTo(THREE_HUNDRED)
        );
    }

    private String createWallet(UUID tenantId, String currency) throws Exception {
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(randomUUID(), currency)
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return jsonMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        ).walletId();
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, String currency) throws Exception {
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new DepositRequest(amount, currency, null)
                        )))
                .andExpect(status().isOk());
    }

    private ErrorDto readError(String responseBody) {
        return jsonMapper.readValue(responseBody, ErrorDto.class);
    }
}
