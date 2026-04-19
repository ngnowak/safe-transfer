package com.nn.safetransfer.outbox;

import com.nn.safetransfer.audit.application.AuditKafkaListener;
import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.infrastructure.mapper.OutboxEventMapper;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.application.CreateWalletCommand;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.WalletApplicationService;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.TWENTY_FIVE;
import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.PUBLISHED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@TestPropertySource(properties = {
        "application.kafka.publishing=true",
        "application.kafka.topics.transfer-completed=wallet.transfer.completed",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer"
})
class KafkaOutboxPublisherIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private AuditKafkaListener auditKafkaListener;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpringDataOutboxEventRepository outboxEventRepository;

    @Autowired
    private SpringDataAuditEventRepository auditEventRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private SpringDataTransferRepository transferRepository;

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        auditEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldPublishOutboxEventToKafkaAndPersistAuditEvent() {
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId, EUR);
        var destinationWallet = createWalletInTx(tenantId, EUR);
        depositInTx(tenantId, sourceWallet.getId().value(), ONE_HUNDRED, EUR);

        var transferResult = transactionTemplate.execute(_ -> transferService.transfer(
                tenantId,
                UUID.randomUUID().toString(),
                new CreateTransferRequest(
                        sourceWallet.getId().value(),
                        destinationWallet.getId().value(),
                        TWENTY_FIVE,
                        EUR.name(),
                        "Kafka integration"
                )
        ));

        assertThat(transferResult).isNotNull();
        assertThat(transferResult.isSuccess()).isTrue();
        var transfer = transferResult.getValue().orElseThrow();
        assertThat(outboxEventRepository.findAll()).hasSize(1);

        var published = outboxPublisher.publishPending(10);

        assertThat(published).isEqualTo(1);
        waitForAuditEvent(transfer);

        var outboxEvent = outboxEventRepository.findAll().getFirst();
        assertThat(outboxEvent.getStatus()).isEqualTo(PUBLISHED.name());
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        assertThat(outboxEvent.getEventType()).isEqualTo(TRANSFER_COMPLETED.name());

        var auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEvent.getAggregateId()).isEqualTo(transfer.getId().value());
        assertThat(auditEvent.getEventType()).isEqualTo(TRANSFER_COMPLETED.name());
        assertThat(auditEvent.getTenantId()).isEqualTo(tenantId.value());
    }

    @Test
    void shouldPersistSingleAuditEventWhenKafkaDeliversDuplicateMessage() throws Exception {
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId, EUR);
        var destinationWallet = createWalletInTx(tenantId, EUR);
        depositInTx(tenantId, sourceWallet.getId().value(), ONE_HUNDRED, EUR);

        var transferResult = transactionTemplate.execute(_ -> transferService.transfer(
                tenantId,
                UUID.randomUUID().toString(),
                new CreateTransferRequest(
                        sourceWallet.getId().value(),
                        destinationWallet.getId().value(),
                        TWENTY_FIVE,
                        EUR.name(),
                        "Duplicate Kafka delivery"
                )
        ));
        assertThat(transferResult).isNotNull();
        assertThat(transferResult.isSuccess()).isTrue();

        var outboxEvent = outboxEventMapper.toDomain(outboxEventRepository.findAll().getFirst());
        var kafkaMessage = jsonMapper.writeValueAsString(outboxEvent);

        auditKafkaListener.consume(kafkaMessage);
        auditKafkaListener.consume(kafkaMessage);

        var auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);
        assertThat(auditEvents.getFirst().getSourceEventId()).isEqualTo(outboxEvent.id());
        assertThat(auditEvents.getFirst().getAggregateId()).isEqualTo(outboxEvent.aggregateId());
    }

    @Test
    void shouldRejectMalformedKafkaMessageWithoutCreatingAuditEvent() {
        assertThatThrownBy(() -> auditKafkaListener.consume("not-json"))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessageContaining("Failed to deserialize Kafka outbox event");

        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    private Wallet createWalletInTx(TenantId tenantId, CurrencyCode currency) {
        return transactionTemplate.execute(_ -> walletApplicationService.handle(CreateWalletCommand.builder()
                        .tenantId(tenantId)
                        .customerId(CustomerId.create())
                        .currency(currency)
                        .build())
                .getValue()
                .orElseThrow());
    }

    private void depositInTx(TenantId tenantId, UUID walletId, BigDecimal amount, CurrencyCode currency) {
        transactionTemplate.executeWithoutResult(_ -> depositService.deposit(
                tenantId,
                new WalletId(walletId),
                new DepositRequest(amount, currency.name(), "Kafka setup deposit")
        ));
    }

    private void waitForAuditEvent(Transfer transfer) {
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    var auditEvents = auditEventRepository.findAll();
                    assertThat(auditEvents).isNotEmpty();
                    assertThat(auditEvents.getFirst().getAggregateId()).isEqualTo(transfer.getId().value());
                });
    }
}
