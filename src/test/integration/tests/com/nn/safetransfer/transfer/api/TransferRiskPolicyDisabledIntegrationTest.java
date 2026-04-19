package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.TEN_THOUSAND_01;
import static com.nn.safetransfer.TestAmounts.TWENTY_THOUSAND;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY;
import static com.nn.safetransfer.transfer.domain.TransferStatus.COMPLETED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "safetransfer.transfer.risk.enabled=false")
class TransferRiskPolicyDisabledIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/{tenantId}/transfers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private SpringDataTransferRepository transferRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private SpringDataOutboxEventRepository outboxEventRepository;

    @Autowired
    private SpringDataAuditEventRepository auditEventRepository;

    @AfterEach
    void cleanUp() {
        auditEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldAllowTransferAboveConfiguredLimitWhenRiskPolicyIsDisabled() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId);
        var destinationWalletId = createWallet(tenantId);
        deposit(tenantId, sourceWalletId, TWENTY_THOUSAND);

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(TEN_THOUSAND_01)
                .currency(EUR.name())
                .reference("Risk disabled")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        var response = jsonMapper.readValue(result.getResponse().getContentAsString(), TransferResponse.class);
        assertAll(
                () -> assertThat(response.amount()).isEqualByComparingTo(TEN_THOUSAND_01),
                () -> assertThat(response.status()).isEqualTo(COMPLETED.name()),
                () -> assertThat(transferRepository.findAll()).hasSize(1),
                () -> assertThat(outboxEventRepository.findAll()).hasSize(1),
                () -> assertThat(ledgerEntryRepository.findAll()).hasSize(3)
        );
    }

    private String createWallet(UUID tenantId) throws Exception {
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateWalletRequest(randomUUID(), EUR.name())
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return jsonMapper.readValue(result.getResponse().getContentAsString(), WalletResponse.class).walletId();
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount) throws Exception {
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new DepositRequest(amount, EUR.name(), "Risk disabled setup")
                        )))
                .andExpect(status().isOk());
    }
}
