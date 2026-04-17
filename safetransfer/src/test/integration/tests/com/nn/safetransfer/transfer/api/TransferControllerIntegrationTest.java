package com.nn.safetransfer.transfer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.common.metrics.MetricName;
import com.nn.safetransfer.common.metrics.MetricTag;
import com.nn.safetransfer.common.metrics.TransferMetricOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.TestAmounts.*;
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
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String EUR = CurrencyCode.EUR.name();
    private static final String COMPLETED = TransferStatus.COMPLETED.name();

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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(TWO_HUNDRED_FIFTY)
                .currency("EUR")
                .reference("Test transfer")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
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
                () -> assertThat(response.amount()).isEqualByComparingTo(TWO_HUNDRED_FIFTY),
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
                () -> assertThat(transferJpa.getAmount()).isEqualByComparingTo(TWO_HUNDRED_FIFTY),
                () -> assertThat(transferJpa.getCurrency()).isEqualTo("EUR"),
                () -> assertThat(transferJpa.getStatus()).isEqualTo("COMPLETED")
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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_FIFTY)
                .currency("EUR")
                .reference("Async audit")
                .build();

        var transferResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var idempotencyKey = UUID.randomUUID().toString();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("Idempotent transfer")
                .build();

        // when - first request
        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
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

        var outboxEvents = outboxEventRepository.findAll();
        var outBoxEventsForTransfer = outboxEvents.stream()
                .filter(outboxEventJpa -> outboxEventJpa.getAggregateId().toString().equals(secondResponse.transferId()))
                .toList();
        assertThat(outBoxEventsForTransfer).hasSize(1);
    }

    @Test
    void shouldReturnExistingTransferWhenIdempotencyKeyIsReusedWithDifferentRequestBody() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var firstDestinationWalletId = createWallet(tenantId, "EUR");
        var secondDestinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var idempotencyKey = UUID.randomUUID().toString();
        var firstRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(firstDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("Original idempotent transfer")
                .build();
        var differentRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(secondDestinationWalletId))
                .amount(TWO_HUNDRED_FIFTY)
                .currency("EUR")
                .reference("Different idempotent transfer")
                .build();

        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // when
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(differentRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        var firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), TransferResponse.class);
        var secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), TransferResponse.class);

        assertAll(
                () -> assertThat(secondResponse.transferId()).isEqualTo(firstResponse.transferId()),
                () -> assertThat(secondResponse.destinationWalletId()).isEqualTo(firstDestinationWalletId),
                () -> assertThat(secondResponse.amount()).isEqualByComparingTo(ONE_HUNDRED),
                () -> assertThat(secondResponse.reference()).isEqualTo("Original idempotent transfer"),
                () -> assertThat(transferRepository.findAll()).hasSize(1),
                () -> assertThat(ledgerEntryRepository.findAll()).hasSize(3),
                () -> assertThat(outboxEventRepository.findAll()).hasSize(1)
        );

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
        var firstTenantId = UUID.randomUUID();
        var secondTenantId = UUID.randomUUID();
        var sharedIdempotencyKey = UUID.randomUUID().toString();

        var firstSourceWalletId = createWallet(firstTenantId, "EUR");
        var firstDestinationWalletId = createWallet(firstTenantId, "EUR");
        deposit(firstTenantId, firstSourceWalletId, "1000.00", "EUR");

        var secondSourceWalletId = createWallet(secondTenantId, "EUR");
        var secondDestinationWalletId = createWallet(secondTenantId, "EUR");
        deposit(secondTenantId, secondSourceWalletId, "1000.00", "EUR");

        var firstRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(firstSourceWalletId))
                .destinationWalletId(UUID.fromString(firstDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("First tenant transfer")
                .build();
        var secondRequest = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(secondSourceWalletId))
                .destinationWalletId(UUID.fromString(secondDestinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("Second tenant transfer")
                .build();

        // when
        var firstResult = mockMvc.perform(post(TRANSFERS_PATH, firstTenantId)
                        .header(IDEMPOTENCY_KEY_HEADER, sharedIdempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        var secondResult = mockMvc.perform(post(TRANSFERS_PATH, secondTenantId)
                        .header(IDEMPOTENCY_KEY_HEADER, sharedIdempotencyKey)
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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_TWENTY)
                .currency("EUR")
                .reference("Get transfer")
                .build();

        var createResult = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
                () -> assertThat(response.amount()).isEqualByComparingTo(ONE_TWENTY),
                () -> assertThat(response.currency()).isEqualTo(EUR),
                () -> assertThat(response.status()).isEqualTo(COMPLETED),
                () -> assertThat(response.reference()).isEqualTo("Get transfer")
        );
    }

    @Test
    void shouldReturnNotFoundWhenTransferDoesNotExist() throws Exception {
        var tenantId = UUID.randomUUID();
        var transferId = UUID.randomUUID();

        var result = mockMvc.perform(get(TRANSFER_PATH, tenantId, transferId))
                .andExpect(status().isNotFound())
                .andReturn();

        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("Transfer with id"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenTransferringToSameWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var walletId = createWallet(tenantId, "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(walletId))
                .destinationWalletId(UUID.fromString(walletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "50.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        // then
        var error = readError(result.getResponse().getContentAsString());
        assertAll(
                () -> assertThat(error.errorId()).isNotNull(),
                () -> assertThat(error.errorMessage()).contains("insufficient funds"),
                () -> assertThat(error.errors()).isNull()
        );
    }

    @Test
    void shouldRecordSuccessTransferMetrics() throws Exception {
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "1000.00", "EUR");

        var counterBefore = counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS);
        var timerBefore = timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS);

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("Metrics success")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "50.00", "EUR");

        var counterBefore = counterCount(MetricName.TRANSFER_CREATED, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS);
        var timerBefore = timerCount(MetricName.TRANSFER_DURATION, MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS);

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .reference("Metrics failure")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
        var tenantId = UUID.randomUUID();
        var destinationWalletId = createWallet(tenantId, "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldReturnNotFoundWhenDestinationWalletDoesNotExist() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.randomUUID())
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldReturnBadRequestWhenCurrencyDoesNotMatchSourceWallet() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("USD")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldReturnBadRequestWhenDestinationWalletHasDifferentCurrency() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "PLN");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldReturnValidationErrorWhenAmountIsZero() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(ZERO)
                .currency("EUR")
                .build();

        // when
        var result = mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
    void shouldTransferEntireBalance() throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "PLN");
        var destinationWalletId = createWallet(tenantId, "PLN");
        deposit(tenantId, sourceWalletId, "300.00", "PLN");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(THREE_HUNDRED)
                .currency("PLN")
                .reference("Full transfer")
                .build();

        // when
        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
        var tenantId = UUID.randomUUID();
        var otherTenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, "EUR");
        var destinationWalletId = createWallet(tenantId, "EUR");
        deposit(tenantId, sourceWalletId, "500.00", "EUR");

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
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
