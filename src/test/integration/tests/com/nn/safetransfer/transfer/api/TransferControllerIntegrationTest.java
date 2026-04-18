package com.nn.safetransfer.transfer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.common.metrics.MetricName;
import com.nn.safetransfer.common.metrics.MetricTag;
import com.nn.safetransfer.common.metrics.TransferMetricOutcome;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.FIFTY;
import static com.nn.safetransfer.TestAmounts.FIVE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.NINE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_FIFTY;
import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_THOUSAND;
import static com.nn.safetransfer.TestAmounts.ONE_TWENTY;
import static com.nn.safetransfer.TestAmounts.SEVEN_HUNDRED_FIFTY;
import static com.nn.safetransfer.TestAmounts.TEN_THOUSAND_01;
import static com.nn.safetransfer.TestAmounts.THREE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.TWENTY_THOUSAND;
import static com.nn.safetransfer.TestAmounts.TWO_HUNDRED_FIFTY;
import static com.nn.safetransfer.TestAmounts.ZERO;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY;
import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.transfer.domain.TransferStatus.COMPLETED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.PLN;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.USD;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebSliceTest
class TransferControllerIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/{tenantId}/transfers";
    private static final String TRANSFER_PATH = "/api/v1/tenants/{tenantId}/transfers/{transferId}";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void cleanUp() {
        auditEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldTransferFundsSuccessfully() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(TWO_HUNDRED_FIFTY)
                .currency(EUR.name())
                .reference("Test transfer")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
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
                () -> assertThat(response.amount()).isEqualByComparingTo(request.amount()),
                () -> assertThat(response.currency()).isEqualTo(request.currency()),
                () -> assertThat(response.status()).isEqualTo(COMPLETED.name()),
                () -> assertThat(response.reference()).isEqualTo(request.reference()),
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
                () -> assertThat(transferJpa.getAmount()).isEqualByComparingTo(TWO_HUNDRED_FIFTY),
                () -> assertThat(transferJpa.getCurrency()).isEqualTo(EUR.name()),
                () -> assertThat(transferJpa.getStatus()).isEqualTo(COMPLETED.name())
        );

        // verify ledger entries: 1 deposit + 1 debit + 1 credit = 3
        var ledgerEntries = ledgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(3);

        var outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        var outboxEvent = outboxEvents.getFirst();
        assertAll(
                () -> assertThat(outboxEvent.getEventType()).isEqualTo(TRANSFER_COMPLETED.name()),
                () -> assertThat(outboxEvent.getAggregateType()).isEqualTo(TRANSFER.name()),
                () -> assertThat(outboxEvent.getAggregateId()).isEqualTo(UUID.fromString(response.transferId())),
                () -> assertThat(outboxEvent.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.NEW.name()),
                () -> assertThat(outboxEvent.getPayload()).contains(response.transferId()),
                () -> assertThat(outboxEvent.getPayload()).contains(tenantId.toString())
        );

        // verify balances
        var sourceBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(sourceWalletId));
        var destinationBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(destinationWalletId));
        assertThat(sourceBalance).isEqualByComparingTo(SEVEN_HUNDRED_FIFTY);
        assertThat(destinationBalance).isEqualByComparingTo(TWO_HUNDRED_FIFTY);
    }

    @Test
    void shouldPublishTransferOutboxEventAndCreateAuditEvent() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_FIFTY)
                .currency(EUR.name())
                .reference("Async audit")
                .build();

        var transferResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var transferResponse = objectMapper.readValue(
                transferResult.getResponse().getContentAsString(), TransferResponse.class
        );
        var createdOutboxEvent = outboxEventRepository.findAll().getFirst();

        // when
        var publishedCount = outboxPublisher.publishPending(10);

        // then
        assertThat(publishedCount).isEqualTo(1);

        var updatedOutboxEvent = outboxEventRepository.findById(createdOutboxEvent.getId()).orElseThrow();
        assertAll(
                () -> assertThat(updatedOutboxEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED.name()),
                () -> assertThat(updatedOutboxEvent.getPublishedAt()).isNotNull()
        );

        var auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);
        var auditEvent = auditEvents.getFirst();
        assertAll(
                () -> assertThat(auditEvent.getSourceEventId()).isEqualTo(createdOutboxEvent.getId()),
                () -> assertThat(auditEvent.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(auditEvent.getAggregateType()).isEqualTo(TRANSFER.name()),
                () -> assertThat(auditEvent.getAggregateId()).isEqualTo(UUID.fromString(transferResponse.transferId())),
                () -> assertThat(auditEvent.getEventType()).isEqualTo(TRANSFER_COMPLETED.name()),
                () -> assertThat(auditEvent.getPayload()).contains(transferResponse.transferId()),
                () -> assertThat(auditEvent.getRecordedAt()).isNotNull(),
                () -> assertThat(auditEvent.getCorrelationId()).isEqualTo(createdOutboxEvent.getCorrelationId()),
                () -> assertThat(auditEvent.getCausationId()).isEqualTo(createdOutboxEvent.getCausationId())
        );
    }

    @Test
    void shouldReturnSameTransferForIdempotentRequest() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var idempotencyKey = randomUUID().toString();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Idempotent transfer")
                .build();

        // when - first request
        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // when - second request with same idempotency key
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, idempotencyKey)
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

        var outboxEvents = outboxEventRepository.findAll();
        var outBoxEventsForTransfer = outboxEvents.stream()
                .filter(outboxEventJpa -> outboxEventJpa.getAggregateId().toString().equals(secondResponse.transferId()))
                .toList();
        assertThat(outBoxEventsForTransfer).hasSize(1);
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsReusedWithDifferentRequestBody() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var firstDestinationWalletId = createWallet(tenantId, EUR.name());
        var secondDestinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var idempotencyKey = randomUUID().toString();
        var firstRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(firstDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Original idempotent transfer")
                .build();
        var differentRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(secondDestinationWalletId))
                .amount(TWO_HUNDRED_FIFTY)
                .currency(EUR.name())
                .reference("Different idempotent transfer")
                .build();

        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // when
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(differentRequest)))
                .andExpect(status().isConflict())
                .andReturn();

        // then
        var firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), TransferResponse.class);
        var error = readError(secondResult.getResponse().getContentAsString());
        var expectedErrorMsg = "Idempotency key '%s' was already used with a different transfer request".formatted(idempotencyKey);

        assertAll(
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(transferRepository.findAll()).hasSize(1),
                () -> assertThat(ledgerEntryRepository.findAll()).hasSize(3),
                () -> assertThat(outboxEventRepository.findAll()).hasSize(1)
        );

        assertThat(transferRepository.findAll().getFirst().getId()).isEqualTo(UUID.fromString(firstResponse.transferId()));

        assertThat(ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(sourceWalletId)))
                .isEqualByComparingTo(NINE_HUNDRED);
        assertThat(ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(firstDestinationWalletId)))
                .isEqualByComparingTo(ONE_HUNDRED);
        assertThat(ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(secondDestinationWalletId)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldAllowSameIdempotencyKeyForDifferentTenants() throws Exception {
        // given
        var firstTenantId = randomUUID();
        var secondTenantId = randomUUID();
        var sharedIdempotencyKey = randomUUID().toString();

        var firstSourceWalletId = createWallet(firstTenantId, EUR.name());
        var firstDestinationWalletId = createWallet(firstTenantId, EUR.name());
        deposit(firstTenantId, firstSourceWalletId, ONE_THOUSAND, EUR.name());

        var secondSourceWalletId = createWallet(secondTenantId, EUR.name());
        var secondDestinationWalletId = createWallet(secondTenantId, EUR.name());
        deposit(secondTenantId, secondSourceWalletId, ONE_THOUSAND, EUR.name());

        var firstRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(firstSourceWalletId))
                .destinationWalletId(UUID.fromString(firstDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("First tenant transfer")
                .build();
        var secondRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(secondSourceWalletId))
                .destinationWalletId(UUID.fromString(secondDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Second tenant transfer")
                .build();

        // when
        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, firstTenantId)
                        .header(IDEMPOTENCY_KEY, sharedIdempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, secondTenantId)
                        .header(IDEMPOTENCY_KEY, sharedIdempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        var firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), TransferResponse.class);
        var secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), TransferResponse.class);

        assertAll(
                () -> assertThat(firstResponse.transferId()).isNotEqualTo(secondResponse.transferId()),
                () -> assertThat(firstResponse.tenantId()).isEqualTo(firstTenantId.toString()),
                () -> assertThat(secondResponse.tenantId()).isEqualTo(secondTenantId.toString()),
                () -> assertThat(transferRepository.findAll()).hasSize(2),
                () -> assertThat(outboxEventRepository.findAll()).hasSize(2)
        );
    }

    @Test
    void shouldGetTransferById() throws Exception {
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_TWENTY)
                .currency(EUR.name())
                .reference("Get transfer")
                .build();

        var createResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var createdTransfer = objectMapper.readValue(createResult.getResponse().getContentAsString(), TransferResponse.class);

        var result = mockMvc.perform(get(TRANSFER_PATH, tenantId, createdTransfer.transferId()))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), TransferResponse.class);

        assertAll(
                () -> assertThat(response.transferId()).isEqualTo(createdTransfer.transferId()),
                () -> assertThat(response.tenantId()).isEqualTo(tenantId.toString()),
                () -> assertThat(response.sourceWalletId()).isEqualTo(sourceWalletId),
                () -> assertThat(response.destinationWalletId()).isEqualTo(destinationWalletId),
                () -> assertThat(response.amount()).isEqualByComparingTo(request.amount()),
                () -> assertThat(response.currency()).isEqualTo(request.currency()),
                () -> assertThat(response.status()).isEqualTo(COMPLETED.name()),
                () -> assertThat(response.reference()).isEqualTo(request.reference())
        );
    }

    @Test
    void shouldReturnNotFoundWhenTransferDoesNotExist() throws Exception {
        var tenantId = randomUUID();
        var transferId = randomUUID();

        var result = mockMvc.perform(get(TRANSFER_PATH, tenantId, transferId))
                .andExpect(status().isNotFound())
                .andReturn();

        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Transfer with id '%s' was not found for tenant '%s'"
                .formatted(transferId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenTransferringToSameWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var walletId = createWallet(tenantId, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(walletId))
                .destinationWalletId(UUID.fromString(walletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo("Source and destination wallets must be different"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnConflictWhenInsufficientFunds() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, FIFTY, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet '%s' has insufficient funds. Available: %s, requested: %s"
                .formatted(sourceWalletId, FIFTY, ONE_HUNDRED);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnConflictWhenTransferExceedsConfiguredRiskLimit() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, TWENTY_THOUSAND, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(TEN_THOUSAND_01)
                .currency(EUR.name())
                .reference("Over configured risk limit")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Transfer amount %s exceeds configured single transfer limit 10000.0"
                .formatted(TEN_THOUSAND_01);
        assertAll(
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(transferRepository.findAll()).isEmpty(),
                () -> assertThat(outboxEventRepository.findAll()).isEmpty(),
                () -> assertThat(ledgerEntryRepository.findAll()).hasSize(1)
        );
    }

    @Test
    void shouldRecordSuccessTransferMetrics() throws Exception {
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var counterBefore = counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS);
        var timerBefore = timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS);

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Metrics success")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS))
                .isEqualTo(counterBefore + 1.0d);
        assertThat(timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS))
                .isEqualTo(timerBefore + 1L);
    }

    @Test
    void shouldRecordFailureTransferMetrics() throws Exception {
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, FIFTY, EUR.name());

        var counterBefore = counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS);
        var timerBefore = timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS);

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Metrics failure")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        assertThat(counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS))
                .isEqualTo(counterBefore + 1.0d);
        assertThat(timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS))
                .isEqualTo(timerBefore + 1L);
    }

    @Test
    void shouldReturnNotFoundWhenSourceWalletDoesNotExist() throws Exception {
        // given
        var tenantId = randomUUID();
        var missingSourceWalletId = randomUUID();
        var destinationWalletId = createWallet(tenantId, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(missingSourceWalletId)
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(missingSourceWalletId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnNotFoundWhenDestinationWalletDoesNotExist() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var missingDestinationWalletId = randomUUID();
        deposit(tenantId, sourceWalletId, FIVE_HUNDRED, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(missingDestinationWalletId)
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(missingDestinationWalletId, tenantId);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenCurrencyDoesNotMatchSourceWallet() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, FIVE_HUNDRED, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("USD")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldReturnBadRequestWhenDestinationWalletHasDifferentCurrency() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, PLN.name());
        deposit(tenantId, sourceWalletId, FIVE_HUNDRED, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = "Wallet currency is '%s' but request currency is '%s'"
                .formatted(PLN, EUR);
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnValidationErrorWhenAmountIsZero() throws Exception {
        // given
        var tenantId = randomUUID();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(randomUUID())
                .destinationWalletId(randomUUID())
                .amount(ZERO)
                .currency(EUR.name())
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        var expectedErrorMsg = """
                Validation failed for argument [2] in public org.springframework.http.ResponseEntity<com.nn.safetransfer.transfer.api.dto.TransferResponse> com.nn.safetransfer.transfer.api.TransferController.createTransfer(java.util.UUID,java.lang.String,com.nn.safetransfer.transfer.api.dto.CreateTransferRequest): [Field error in object 'createTransferRequest' on field 'amount': rejected value [0.00]; codes [DecimalMin.createTransferRequest.amount,DecimalMin.amount,DecimalMin.java.math.BigDecimal,DecimalMin]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [createTransferRequest.amount,amount]; arguments []; default message [amount],true,0.01]; default message [must be greater than or equal to 0.01]]\s""";
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).isEqualTo(expectedErrorMsg),
                () -> assertThat(error.errors()).contains("amount: must be greater than or equal to 0.01")
        );
    }

    @Test
    void shouldTransferEntireBalance() throws Exception {
        // given
        var tenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, PLN.name());
        var destinationWalletId = createWallet(tenantId, PLN.name());
        deposit(tenantId, sourceWalletId, THREE_HUNDRED, PLN.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(THREE_HUNDRED)
                .currency(PLN.name())
                .reference("Full transfer")
                .build();

        // when
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // then
        var sourceBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(sourceWalletId));
        var destinationBalance = ledgerEntryRepository.calculateBalance(tenantId, UUID.fromString(destinationWalletId));
        assertThat(sourceBalance).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(destinationBalance).isEqualByComparingTo(THREE_HUNDRED);
    }

    @Test
    void shouldReturnNotFoundWhenWalletBelongsToDifferentTenant() throws Exception {
        // given
        var tenantId = randomUUID();
        var otherTenantId = randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, FIVE_HUNDRED, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .build();

        // when / then - use different tenant
        mockMvc.perform(post(TRANSFERS_PATH, otherTenantId)
                        .header(IDEMPOTENCY_KEY, randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private String createWallet(UUID tenantId, String currency) throws Exception {
        var result = mockMvc.perform(post(WALLETS_PATH, tenantId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWalletRequest(randomUUID(), currency)
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class
        ).walletId();
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, String currency) throws Exception {
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(amount, currency, "Test deposit")
                        )))
                .andExpect(status().isOk());
    }

    private ErrorDto readError(String responseBody) throws Exception {
        return objectMapper.readValue(responseBody, ErrorDto.class);
    }

    private double counterCount(MetricName meterName, MetricTag tagKey, TransferMetricOutcome tagValue) {
        var counter = meterRegistry.find(meterName.getValue())
                .tag(tagKey.getValue(), tagValue.getTagValue())
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    private long timerCount(MetricName meterName, MetricTag tagKey, TransferMetricOutcome tagValue) {
        var timer = meterRegistry.find(meterName.getValue())
                .tag(tagKey.getValue(), tagValue.getTagValue())
                .timer();
        return timer == null ? 0L : timer.count();
    }
}
