package com.nn.safetransfer.transfer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebSliceTest
class TransferControllerIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/{tenantId}/transfers";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private SpringDataTransferRepository transferRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldTransferFundsSuccessfully() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("250.00"))
                .currency("EUR")
                .reference("Test transfer")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransferResponse.class
        );

        assertAll(
                () -> assertThat(response.transferId()).isNotNull(),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.sourceWalletId()).isEqualTo(sourceWalletId),
                () -> assertThat(response.destinationWalletId()).isEqualTo(destinationWalletId),
                () -> assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("250.00")),
                () -> assertThat(response.currency()).isEqualTo("EUR"),
                () -> assertThat(response.status()).isEqualTo("COMPLETED"),
                () -> assertThat(response.reference()).isEqualTo("Test transfer"),
                () -> assertThat(response.createdAt()).isNotNull()
        );

        // verify database state
        var transfers = transferRepository.findAll();
        assertThat(transfers).hasSize(1);

        var transferJpa = transfers.getFirst();
        assertAll(
                () -> assertThat(transferJpa.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(transferJpa.getSourceWalletId()).isEqualTo(UUID.fromString(sourceWalletId)),
                () -> assertThat(transferJpa.getDestinationWalletId()).isEqualTo(UUID.fromString(destinationWalletId)),
                () -> assertThat(transferJpa.getAmount()).isEqualByComparingTo(new BigDecimal("250.00")),
                () -> assertThat(transferJpa.getCurrency()).isEqualTo("EUR"),
                () -> assertThat(transferJpa.getStatus()).isEqualTo("COMPLETED")
        );

        // verify ledger entries: 1 deposit + 1 debit + 1 credit = 3
        var ledgerEntries = ledgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(3);

        // verify balances
        var sourceBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(sourceWalletId));
        var destinationBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(destinationWalletId));
        assertThat(sourceBalance).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(destinationBalance).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void shouldReturnSameTransferForIdempotentRequest() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var idempotencyKey = UUID.randomUUID().toString();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .reference("Idempotent transfer")
                .build();

        // when - first request
        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // when - second request with same idempotency key
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), TransferResponse.class
        );
        var secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), TransferResponse.class
        );

        assertThat(firstResponse.transferId()).isEqualTo(secondResponse.transferId());

        // verify only one transfer and one pair of ledger entries were created
        assertThat(transferRepository.findAll()).hasSize(1);
        // 1 deposit + 1 debit + 1 credit = 3
        assertThat(ledgerEntryRepository.findAll()).hasSize(3);
    }

    @Test
    void shouldReturnBadRequestWhenTransferringToSameWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(walletId))
                .destinationWalletId(UUID.fromString(walletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid transfer"))
                .andExpect(jsonPath("$.detail").value("Source and destination wallets must be different"));
    }

    @Test
    void shouldReturnConflictWhenInsufficientFunds() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "50.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient funds"));
    }

    @Test
    void shouldReturnNotFoundWhenSourceWalletDoesNotExist() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var destinationWalletId = createWallet(tenantId, "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Wallet not found"));
    }

    @Test
    void shouldReturnNotFoundWhenDestinationWalletDoesNotExist() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Wallet not found"));
    }

    @Test
    void shouldReturnBadRequestWhenCurrencyDoesNotMatchSourceWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wallet currency mismatch"));
    }

    @Test
    void shouldReturnBadRequestWhenDestinationWalletHasDifferentCurrency() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "PLN");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Wallet currency mismatch"));
    }

    @Test
    void shouldReturnValidationErrorWhenAmountIsZero() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal("0.00"))
                .currency("EUR")
                .build();

        // when / then
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void shouldTransferEntireBalance() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "PLN");
        var destinationWalletId = createWallet(tenantId, "PLN");
        deposit(tenantId, sourceWalletId, "300.00", "PLN");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("300.00"))
                .currency("PLN")
                .reference("Full transfer")
                .build();

        // when
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        var sourceBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(sourceWalletId));
        var destinationBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(destinationWalletId));
        assertThat(sourceBalance).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(destinationBalance).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void shouldReturnNotFoundWhenWalletBelongsToDifferentTenant() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var otherTenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .build();

        // when / then - use different tenant
        mockMvc.perform(post(TRANSFERS_PATH, otherTenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
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
                                new DepositRequest(new BigDecimal(amount), currency, "Test deposit")
                        )))
                .andExpect(status().isOk());
    }
}
