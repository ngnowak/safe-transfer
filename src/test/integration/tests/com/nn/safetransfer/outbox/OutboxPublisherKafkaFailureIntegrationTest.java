package com.nn.safetransfer.outbox;

import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.outbox.application.ApplicationKafkaProperties;
import com.nn.safetransfer.outbox.application.KafkaOutboxEventDispatcher;
import com.nn.safetransfer.outbox.application.OutboxEventDispatcher;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.application.CreateWalletCommand;
import com.nn.safetransfer.wallet.application.DepositCommand;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.WalletApplicationService;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.Wallet;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FAILED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FATAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@IntegrationTest
@Import(OutboxPublisherKafkaFailureIntegrationTest.UnreachableKafkaDispatcherConfig.class)
@TestPropertySource(properties = {
        "application.kafka.publishing=true",
        "application.kafka.topics.transfer-completed=wallet.transfer.completed",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer"
})
class OutboxPublisherKafkaFailureIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private OutboxPublisher outboxPublisher;

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
    void shouldRetryAndEventuallyMarkFatalWhenRealKafkaPublishFails() {
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId);
        var destinationWallet = createWalletInTx(tenantId);
        depositInTx(tenantId, sourceWallet.getId());

        var transferResult = transactionTemplate.execute(_ -> transferService.transfer(
                tenantId,
                UUID.randomUUID().toString(),
                CreateTransferRequest.builder()
                        .sourceWalletId(sourceWallet.getId().value())
                        .destinationWalletId(destinationWallet.getId().value())
                        .amount(ONE_HUNDRED)
                        .currency(CurrencyCode.EUR.name())
                        .reference("Kafka failure retry")
                        .build()
        ));
        assertThat(transferResult).isNotNull();
        assertThat(transferResult.isSuccess()).isTrue();

        var firstRun = outboxPublisher.publishPending(10);
        var afterFirstRun = outboxEventRepository.findAll().getFirst();
        var secondRun = outboxPublisher.publishPending(10);
        var afterSecondRun = outboxEventRepository.findAll().getFirst();
        var thirdRun = outboxPublisher.publishPending(10);
        var afterThirdRun = outboxEventRepository.findAll().getFirst();
        var fourthRun = outboxPublisher.publishPending(10);

        assertAll(
                () -> assertThat(firstRun).isZero(),
                () -> assertThat(afterFirstRun.getStatus()).isEqualTo(FAILED.name()),
                () -> assertThat(afterFirstRun.getRetryCount()).isEqualTo(1),
                () -> assertThat(afterFirstRun.getPublishedAt()).isNull(),
                () -> assertThat(secondRun).isZero(),
                () -> assertThat(afterSecondRun.getStatus()).isEqualTo(FAILED.name()),
                () -> assertThat(afterSecondRun.getRetryCount()).isEqualTo(2),
                () -> assertThat(afterSecondRun.getPublishedAt()).isNull(),
                () -> assertThat(thirdRun).isZero(),
                () -> assertThat(afterThirdRun.getStatus()).isEqualTo(FATAL.name()),
                () -> assertThat(afterThirdRun.getRetryCount()).isEqualTo(3),
                () -> assertThat(afterThirdRun.getPublishedAt()).isNull(),
                () -> assertThat(fourthRun).isZero(),
                () -> assertThat(auditEventRepository.findAll()).isEmpty()
        );
    }

    private Wallet createWalletInTx(TenantId tenantId) {
        return transactionTemplate.execute(_ -> walletApplicationService.handle(CreateWalletCommand.builder()
                        .tenantId(tenantId)
                        .customerId(CustomerId.create())
                        .currency(CurrencyCode.EUR)
                        .build())
                .getValue()
                .orElseThrow());
    }

    private void depositInTx(TenantId tenantId, WalletId walletId) {
        transactionTemplate.executeWithoutResult(_ -> depositService.deposit(
                tenantId,
                walletId,
                new DepositCommand(ONE_HUNDRED, CurrencyCode.EUR.name(), "Kafka failure setup deposit")
        ));
    }

    @TestConfiguration
    static class UnreachableKafkaDispatcherConfig {

        @Bean
        @Primary
        OutboxEventDispatcher unreachableKafkaDispatcher(JsonMapper jsonMapper) {
            var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.MAX_BLOCK_MS_CONFIG, "500",
                    ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "500",
                    ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "1000",
                    ProducerConfig.RETRIES_CONFIG, "0"
            ));
            var kafkaTemplate = new KafkaTemplate<>(producerFactory);
            var properties = new ApplicationKafkaProperties(
                    true,
                    new ApplicationKafkaProperties.Topics("wallet.transfer.completed")
            );
            return new KafkaOutboxEventDispatcher(kafkaTemplate, jsonMapper, properties);
        }
    }
}
