package com.nn.safetransfer;

import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.ledger.infrastructure.persistence.SpringDataLedgerEntryRepository;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.application.CreateWalletCommand;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.WalletApplicationService;
import com.nn.safetransfer.wallet.application.WalletError;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ConcurrencyIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @Autowired
    private SpringDataTransferRepository transferRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @Timeout(30)
    void shouldPreventDoubleSpendingUnderConcurrentTransfers() throws Exception {
        // given
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId);
        depositInTx(tenantId, sourceWallet.getId(), "100.00");

        int threadCount = 10;
        var destinationWallets = new ArrayList<Wallet>();
        for (int i = 0; i < threadCount; i++) {
            destinationWallets.add(createWalletInTx(tenantId));
        }

        var readyLatch = new CountDownLatch(threadCount);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threadCount);

        // when
        var futures = new ArrayList<Future<TransferResult>>();
        for (int i = 0; i < threadCount; i++) {
            var destWallet = destinationWallets.get(i);
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return executeTransfer(tenantId, sourceWallet.getId(), destWallet.getId(),
                        "100.00", UUID.randomUUID().toString());
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        var results = collectResults(futures);
        pool.shutdown();

        // then
        long successes = results.stream().filter(r -> r.success).count();
        long failures = results.stream().filter(r -> !r.success && r.error instanceof TransferError.InsufficientFunds).count();

        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(9);

        var sourceBalance = ledgerEntryRepository.calculateBalance(
                tenantId.value(), sourceWallet.getId().value());
        assertThat(sourceBalance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @Timeout(10)
    void shouldNotDeadlockOnBidirectionalTransfers() throws Exception {
        // given
        var tenantId = TenantId.create();
        var walletA = createWalletInTx(tenantId);
        var walletB = createWalletInTx(tenantId);
        depositInTx(tenantId, walletA.getId(), "500.00");
        depositInTx(tenantId, walletB.getId(), "500.00");

        var readyLatch = new CountDownLatch(2);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        // when - A->B and B->A simultaneously
        var futureAB = pool.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return executeTransfer(tenantId, walletA.getId(), walletB.getId(),
                    "100.00", UUID.randomUUID().toString());
        });
        var futureBA = pool.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return executeTransfer(tenantId, walletB.getId(), walletA.getId(),
                    "100.00", UUID.randomUUID().toString());
        });

        readyLatch.await();
        startLatch.countDown();

        var resultAB = futureAB.get();
        var resultBA = futureBA.get();
        pool.shutdown();

        // then - both should succeed (lock ordering prevents deadlock)
        assertThat(resultAB.success).isTrue();
        assertThat(resultBA.success).isTrue();

        var balanceA = ledgerEntryRepository.calculateBalance(
                tenantId.value(), walletA.getId().value());
        var balanceB = ledgerEntryRepository.calculateBalance(
                tenantId.value(), walletB.getId().value());

        assertThat(balanceA).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balanceB).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @Timeout(30)
    void shouldCreateExactlyOneTransferForConcurrentIdempotentRequests() throws Exception {
        // given
        var tenantId = TenantId.create();
        var sourceWallet = createWalletInTx(tenantId);
        var destinationWallet = createWalletInTx(tenantId);
        depositInTx(tenantId, sourceWallet.getId(), "1000.00");

        var idempotencyKey = UUID.randomUUID().toString();
        int threadCount = 20;
        var readyLatch = new CountDownLatch(threadCount);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threadCount);

        // when
        var futures = new ArrayList<Future<TransferResult>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return executeTransfer(tenantId, sourceWallet.getId(), destinationWallet.getId(),
                        "100.00", idempotencyKey);
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        var results = collectResults(futures);
        pool.shutdown();

        // then - exactly 1 transfer in DB
        assertThat(transferRepository.findAll()).hasSize(1);

        // all successful threads should return the same transferId
        var successfulTransferIds = results.stream()
                .filter(r -> r.success)
                .map(r -> r.transfer.getId().value())
                .distinct()
                .toList();
        assertThat(successfulTransferIds).hasSize(1);

        // balance debited only once
        var sourceBalance = ledgerEntryRepository.calculateBalance(
                tenantId.value(), sourceWallet.getId().value());
        assertThat(sourceBalance).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @Timeout(30)
    void shouldCreateExactlyOneWalletUnderConcurrentDuplicateCreation() throws Exception {
        // given
        var tenantId = TenantId.create();
        var customerId = CustomerId.create();
        var currency = CurrencyCode.EUR;

        int threadCount = 10;
        var readyLatch = new CountDownLatch(threadCount);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threadCount);

        // when
        var futures = new ArrayList<Future<Result<WalletError, Wallet>>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    return transactionTemplate.execute(status ->
                            walletApplicationService.handle(
                                    new CreateWalletCommand(tenantId, customerId, currency)
                            )
                    );
                } catch (Exception e) {
                    return Result.failure(new WalletError.OtherError(e.getMessage()));
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        var results = new ArrayList<Result<WalletError, Wallet>>();
        for (var future : futures) {
            results.add(future.get());
        }
        pool.shutdown();

        // then - exactly 1 wallet in DB
        var wallets = walletRepository.findAll();
        assertThat(wallets).hasSize(1);

        long successes = results.stream()
                .filter(Result::isSuccess).count();
        assertThat(successes).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Timeout(30)
    void shouldNotLoseDepositsUnderConcurrentAccess() throws Exception {
        // given
        var tenantId = TenantId.create();
        var wallet = createWalletInTx(tenantId);

        int threadCount = 50;
        var readyLatch = new CountDownLatch(threadCount);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threadCount);

        // when
        var futures = new ArrayList<Future<Boolean>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                transactionTemplate.execute(status ->
                        depositService.deposit(
                                tenantId,
                                wallet.getId(),
                                new DepositRequest(new BigDecimal("10.00"), "EUR", "Concurrent deposit")
                        )
                );
                return true;
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (var future : futures) {
            future.get();
        }
        pool.shutdown();

        // then - all 50 deposits should be recorded
        var ledgerEntries = ledgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(50);

        var balance = ledgerEntryRepository.calculateBalance(
                tenantId.value(), wallet.getId().value());
        assertThat(balance).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @Timeout(30)
    void shouldMaintainBalanceConsistencyWithMultipleConcurrentTransfers() throws Exception {
        // given
        var tenantId = TenantId.create();
        var walletA = createWalletInTx(tenantId);
        depositInTx(tenantId, walletA.getId(), "250.00");

        var amounts = List.of("200.00", "100.00", "150.00");
        var destinationWallets = new ArrayList<Wallet>();
        for (int i = 0; i < amounts.size(); i++) {
            destinationWallets.add(createWalletInTx(tenantId));
        }

        BigDecimal initialTotal = new BigDecimal("250.00");

        int threadCount = amounts.size();
        var readyLatch = new CountDownLatch(threadCount);
        var startLatch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threadCount);

        // when
        var futures = new ArrayList<Future<TransferResult>>();
        for (int i = 0; i < threadCount; i++) {
            var amount = amounts.get(i);
            var destWallet = destinationWallets.get(i);
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return executeTransfer(tenantId, walletA.getId(), destWallet.getId(),
                        amount, UUID.randomUUID().toString());
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        var results = collectResults(futures);
        pool.shutdown();

        // then
        var sourceBalance = ledgerEntryRepository.calculateBalance(
                tenantId.value(), walletA.getId().value());
        assertThat(sourceBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // at least 1 transfer must fail (200+100+150 = 450 > 250)
        long failures = results.stream().filter(r -> !r.success).count();
        assertThat(failures).isGreaterThanOrEqualTo(1);

        // conservation of money: sum of all balances must equal initial total
        var allWalletIds = new ArrayList<WalletId>();
        allWalletIds.add(walletA.getId());
        destinationWallets.forEach(w -> allWalletIds.add(w.getId()));

        var totalBalance = BigDecimal.ZERO;
        for (var walletId : allWalletIds) {
            totalBalance = totalBalance.add(
                    ledgerEntryRepository.calculateBalance(tenantId.value(), walletId.value())
            );
        }
        assertThat(totalBalance).isEqualByComparingTo(initialTotal);
    }

    // --- helpers ---

    private Wallet createWalletInTx(TenantId tenantId) {
        return transactionTemplate.execute(status ->
                walletApplicationService.handle(
                        new CreateWalletCommand(tenantId, CustomerId.create(), CurrencyCode.EUR)
                ).getValue().orElseThrow(() -> new IllegalArgumentException("cannot get wallet"))
        );
    }

    private void depositInTx(TenantId tenantId, WalletId walletId, String amount) {
        transactionTemplate.execute(status -> {
            depositService.deposit(tenantId, walletId,
                    new DepositRequest(new BigDecimal(amount), "EUR", "Setup deposit"));
            return null;
        });
    }

    private TransferResult executeTransfer(TenantId tenantId, WalletId sourceId,
                                           WalletId destinationId, String amount,
                                           String idempotencyKey) {
        try {
            var result = transactionTemplate.execute(status ->
                    transferService.transfer(
                            tenantId,
                            idempotencyKey,
                            CreateTransferRequest.builder()
                                    .sourceWalletId(sourceId.value())
                                    .destinationWalletId(destinationId.value())
                                    .amount(new BigDecimal(amount))
                                    .currency("EUR")
                                    .reference("Concurrent transfer")
                                    .build()
                    )
            );
            return switch (result) {
                case Result.Success<TransferError, Transfer> success -> new TransferResult(true, success.value(), null);
                case Result.Failure<TransferError, Transfer> failure ->
                        new TransferResult(false, null, failure.error());
            };
        } catch (Exception e) {
            return new TransferResult(false, null, null);
        }
    }

    private List<TransferResult> collectResults(List<Future<TransferResult>> futures) throws Exception {
        var results = new ArrayList<TransferResult>();
        for (var future : futures) {
            results.add(future.get());
        }
        return results;
    }

    private record TransferResult(boolean success, Transfer transfer, TransferError error) {
    }

    private record WalletResult(boolean success, Wallet wallet, Exception exception) {
    }
}
