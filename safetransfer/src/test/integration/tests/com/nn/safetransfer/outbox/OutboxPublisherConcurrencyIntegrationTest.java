package com.nn.safetransfer.outbox;

import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.audit.infrastructure.persistence.SpringDataAuditEventRepository;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.outbox.application.OutboxPublisher;
import com.nn.safetransfer.outbox.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.application.TransferError;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OutboxPublisherConcurrencyIntegrationTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private SpringDataAuditEventRepository auditEventRepository;

    @Autowired
    private SpringDataOutboxEventRepository outboxEventRepository;

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
    @Timeout(20)
    void shouldProcessSingleOutboxEventOnlyOnceWhenPublishingConcurrently() throws Exception {
        // given
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId);
        var destinationWallet = createWalletInTx(tenantId);
        depositInTx(tenantId, sourceWallet.getId(), "500.00");
        createTransferInTx(tenantId, sourceWallet.getId(), destinationWallet.getId(), "100.00");

        assertThat(outboxEventRepository.findAll()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(auditEventRepository.findAll()).isEmpty();

        var readyLatch = new CountDownLatch(2);
        var startLatch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        // when
        var futures = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return transactionTemplate.execute(status -> outboxPublisher.publishPending(10));
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        var firstResult = futures.get(0).get();
        var secondResult = futures.get(1).get();
        executor.shutdown();

        // then
        assertThat(firstResult + secondResult).isGreaterThanOrEqualTo(1);
        assertThat(auditEventRepository.findAll()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(outboxEventRepository.findAll()).hasSize(1);

        var outboxEvent = outboxEventRepository.findAll().getFirst();
        assertThat(outboxEvent.getStatus()).isEqualTo("PUBLISHED");
        assertThat(outboxEvent.getRetryCount()).isZero();
        assertThat(outboxEvent.getPublishedAt()).isNotNull();

        var auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEvent.getSourceEventId()).isEqualTo(outboxEvent.getId());
    }

    private Wallet createWalletInTx(TenantId tenantId) {
        return transactionTemplate.execute(status ->
                walletApplicationService.handle(
                        new CreateWalletCommand(tenantId, CustomerId.create(), CurrencyCode.EUR)
                ).getValue().orElseThrow(() -> new IllegalArgumentException("No wallet"))
        );
    }

    private void depositInTx(TenantId tenantId, WalletId walletId, String amount) {
        transactionTemplate.execute(status -> {
            depositService.deposit(
                    tenantId,
                    walletId,
                    new DepositRequest(new BigDecimal(amount), "EUR", "Setup deposit")
            );
            return null;
        });
    }

    private void createTransferInTx(TenantId tenantId, WalletId sourceWalletId, WalletId destinationWalletId, String amount) {
        var result = transactionTemplate.execute(status ->
                transferService.transfer(
                        tenantId,
                        UUID.randomUUID().toString(),
                        CreateTransferRequest.builder()
                                .sourceWalletId(sourceWalletId.value())
                                .destinationWalletId(destinationWalletId.value())
                                .amount(new BigDecimal(amount))
                                .currency("EUR")
                                .reference("Concurrent publisher test")
                                .build()
                )
        );

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<TransferError, Transfer>) result).value()).isNotNull();
    }
}
