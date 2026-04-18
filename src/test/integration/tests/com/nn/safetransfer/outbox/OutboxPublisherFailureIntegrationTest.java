package com.nn.safetransfer.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.annotation.WebSliceTest;
import com.nn.safetransfer.audit.application.AuditConsumer;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nn.safetransfer.TestAmounts.*;
import static com.nn.safetransfer.common.api.ApiHeaders.IDEMPOTENCY_KEY;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebSliceTest
@Import(OutboxPublisherFailureIntegrationTest.FailingAuditConsumerConfig.class)
class OutboxPublisherFailureIntegrationTest {

    private static final String WALLETS_PATH = "/api/v1/tenants/{tenantId}/wallets";
    private static final String DEPOSITS_PATH = "/api/v1/tenants/{tenantId}/wallets/{walletId}/deposits";
    private static final String TRANSFERS_PATH = "/api/v1/tenants/{tenantId}/transfers";

    @Autowired
    private MockMvc mockMvc;

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
    private FailingAuditConsumer failingAuditConsumer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void cleanUp() {
        failingAuditConsumer.reset();
        auditEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldMarkOutboxEventAsFailedWhenPublishingFails() throws Exception {
        // given
        createTransferProducingOutboxEvent();

        // when
        var published = outboxPublisher.publishPending(10);

        // then
        var outboxEvent = outboxEventRepository.findAll().getFirst();
        assertAll(
                () -> assertThat(published).isZero(),
                () -> assertThat(failingAuditConsumer.invocationCount()).isEqualTo(1),
                () -> assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.FAILED.name()),
                () -> assertThat(outboxEvent.getRetryCount()).isEqualTo(1),
                () -> assertThat(outboxEvent.getPublishedAt()).isNull(),
                () -> assertThat(auditEventRepository.findAll()).isEmpty()
        );
    }

    @Test
    void shouldMarkOutboxEventAsFatalAfterMaxRetriesAndStopRetrying() throws Exception {
        // given
        createTransferProducingOutboxEvent();

        // when
        var firstRun = outboxPublisher.publishPending(10);
        var secondRun = outboxPublisher.publishPending(10);
        var thirdRun = outboxPublisher.publishPending(10);
        var fourthRun = outboxPublisher.publishPending(10);

        // then
        var outboxEvent = outboxEventRepository.findAll().getFirst();
        assertAll(
                () -> assertThat(firstRun).isZero(),
                () -> assertThat(secondRun).isZero(),
                () -> assertThat(thirdRun).isZero(),
                () -> assertThat(fourthRun).isZero(),
                () -> assertThat(failingAuditConsumer.invocationCount()).isEqualTo(3),
                () -> assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.FATAL.name()),
                () -> assertThat(outboxEvent.getRetryCount()).isEqualTo(3),
                () -> assertThat(outboxEvent.getPublishedAt()).isNull(),
                () -> assertThat(auditEventRepository.findAll()).isEmpty()
        );
    }

    @Test
    void shouldRetryStaleProcessingOutboxEvent() throws Exception {
        // given
        createTransferProducingOutboxEvent();
        var outboxEventId = outboxEventRepository.findAll().getFirst().getId();
        jdbcTemplate.update("""
                update outbox_event
                set status = ?,
                    claimed_at = now() - interval '10 minutes'
                where id = ?
                """, OutboxStatus.PROCESSING.name(), outboxEventId);

        // when
        var published = outboxPublisher.publishPending(10);

        // then
        var outboxEvent = outboxEventRepository.findById(outboxEventId).orElseThrow();
        assertAll(
                () -> assertThat(published).isZero(),
                () -> assertThat(failingAuditConsumer.invocationCount()).isEqualTo(1),
                () -> assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.FAILED.name()),
                () -> assertThat(outboxEvent.getRetryCount()).isEqualTo(1),
                () -> assertThat(outboxEvent.getClaimedAt()).isNull()
        );
    }

    private void createTransferProducingOutboxEvent() throws Exception {
        var tenantId = UUID.randomUUID();
        var sourceWalletId = createWallet(tenantId, EUR.name());
        var destinationWalletId = createWallet(tenantId, EUR.name());
        deposit(tenantId, sourceWalletId, ONE_THOUSAND, EUR.name());

        var request = CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWalletId))
                .destinationWalletId(UUID.fromString(destinationWalletId))
                .amount(ONE_HUNDRED)
                .currency(EUR.name())
                .reference("Failing async audit")
                .build();

        mockMvc.perform(post(TRANSFERS_PATH, tenantId)
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(outboxEventRepository.findAll()).hasSize(1);
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
                result.getResponse().getContentAsString(), WalletResponse.class).walletId();
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, String currency) throws Exception {
        mockMvc.perform(post(DEPOSITS_PATH, tenantId, walletId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(amount, currency, "Setup deposit")
                        )))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class FailingAuditConsumerConfig {

        @Bean
        FailingAuditConsumer failingAuditConsumer() {
            return new FailingAuditConsumer();
        }

        @Bean
        @Primary
        AuditConsumer auditConsumer(FailingAuditConsumer failingAuditConsumer) {
            return failingAuditConsumer;
        }
    }

    static class FailingAuditConsumer implements AuditConsumer {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public void consume(OutboxEvent outboxEvent) throws OutboxProcessingException {
            invocationCount.incrementAndGet();
            throw new OutboxProcessingException("Forced consumer failure", new IllegalStateException("forced"));
        }

        int invocationCount() {
            return invocationCount.get();
        }

        void reset() {
            invocationCount.set(0);
        }
    }
}
