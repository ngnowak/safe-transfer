package com.nn.safetransfer.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebSliceTest
class WalletControllerIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String WALLET_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
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
        var request = new CreateWalletRequest(customerId, "EUR");

        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // when / then
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Wallet already exists for this tenant, customer, and currency"));
    }

    @Test
    void shouldReturnValidationErrorWhenCurrencyIsBlank() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = new CreateWalletRequest(UUID.randomUUID(), "");

        // when / then
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void shouldReturnValidationErrorWhenCustomerIdIsNull() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var body = """
                {"customerId": null, "currency": "EUR"}
                """;

        // when / then
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void shouldReturnErrorWhenCurrencyCodeIsInvalid() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = new CreateWalletRequest(UUID.randomUUID(), "INVALID");

        // when / then
        mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").exists());
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

        // when / then
        mockMvc.perform(get(WALLET_PATH, tenantId, walletId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Wallet not found"))
                .andExpect(jsonPath("$.detail").exists());
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

        // when / then
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Wallet not found"));
    }

    @Test
    void shouldReturnBadRequestWhenDepositCurrencyMismatch() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");
        var depositRequest = new DepositRequest(new BigDecimal("100.00"), "USD", null);

        // when / then
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wallet currency mismatch"));
    }

    @Test
    void shouldReturnValidationErrorWhenDepositAmountIsZero() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");
        var depositRequest = new DepositRequest(new BigDecimal("0.00"), "EUR", null);

        // when / then
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest());
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
}
